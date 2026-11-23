package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Categories come most used first (D31). The clock stands at 23:00 on 2026-11-15 in the family's time zone
 * (America/Fortaleza), already 2026-11-16 in UTC: the 90-day window starts on 2026-08-18 only when "today" is the
 * family's date.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CategoriesIT {

    private static final List<String> DEFAULT_EXPENSES = List.of(
            "Mercado",
            "Alimentação fora",
            "Transporte",
            "Moradia",
            "Saúde",
            "Lazer",
            "Contas e serviços",
            "Compras",
            "Educação",
            "Pets",
            "Presentes e doações",
            "Impostos e taxas",
            "Outros");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    private UUID userId;
    private UUID workspaceId;
    private UUID membershipId;

    @BeforeEach
    void family() {
        userId = users.create("Ana Teste", "ana@example.com");
        workspaceId = workspaces.createFor(userId);
        membershipId = workspaces.membershipOf(workspaceId, userId);
    }

    @Test
    void expenseCategoriesInDefaultOrder() {
        var result = categories(userId, "EXPENSE");
        var mercado = new HashMap<String, Object>();
        mercado.put("id", ledger.categoryOf(workspaceId, "EXPENSE", "Mercado").toString());
        mercado.put("name", "Mercado");
        mercado.put("kind", "EXPENSE");
        mercado.put("icon", "🛒");
        mercado.put("color", null);
        mercado.put("parentId", null);
        mercado.put("archivedAt", null);
        mercado.put("isFixed", false);

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        assertThat(names(result)).containsExactlyElementsOf(DEFAULT_EXPENSES);
        assertThat(result).bodyJson().extractingPath("$[0]").isEqualTo(mercado);
    }

    @Test
    void incomeCategories() {
        var result = categories(userId, "INCOME");

        assertThat(result).hasStatusOk();
        assertThat(names(result))
                .containsExactly("Salário", "Renda extra", "Rendimentos", "Reembolsos", "Presentes", "Outros");
        assertThat(result).bodyJson().extractingPath("$[*].kind").asArray().containsOnly("INCOME");
    }

    @Test
    void mostUsedFirst() {
        expense("Pets", LocalDate.of(2026, 11, 10));
        expense("Pets", LocalDate.of(2026, 11, 10));
        expense("Lazer", LocalDate.of(2026, 11, 10));

        var result = categories(userId, "EXPENSE");

        assertThat(names(result))
                .containsExactly(
                        "Pets",
                        "Lazer",
                        "Mercado",
                        "Alimentação fora",
                        "Transporte",
                        "Moradia",
                        "Saúde",
                        "Contas e serviços",
                        "Compras",
                        "Educação",
                        "Presentes e doações",
                        "Impostos e taxas",
                        "Outros");
    }

    @Test
    void usageWindowIs90Days() {
        expense("Lazer", LocalDate.of(2026, 8, 18));
        expense("Transporte", LocalDate.of(2026, 8, 17));
        expense("Transporte", LocalDate.of(2026, 8, 17));
        expense("Transporte", LocalDate.of(2026, 8, 17));

        var result = categories(userId, "EXPENSE");

        assertThat(names(result))
                .containsExactly(
                        "Lazer",
                        "Mercado",
                        "Alimentação fora",
                        "Transporte",
                        "Moradia",
                        "Saúde",
                        "Contas e serviços",
                        "Compras",
                        "Educação",
                        "Pets",
                        "Presentes e doações",
                        "Impostos e taxas",
                        "Outros");
    }

    @Test
    void deletedTransactionsDoNotRank() {
        expense("Pets", LocalDate.of(2026, 11, 10));
        expense("Pets", LocalDate.of(2026, 11, 10));
        jdbc.sql("update transactions set deleted_at = now() where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .update();

        var result = categories(userId, "EXPENSE");

        assertThat(names(result)).containsExactlyElementsOf(DEFAULT_EXPENSES);
    }

    /** The window has a start and no end: an expense dated after the family's today still counts (D31). */
    @Test
    void futureDatedExpensesRank() {
        expense("Lazer", LocalDate.of(2026, 12, 20));

        var result = categories(userId, "EXPENSE");

        assertThat(names(result)).startsWith("Lazer", "Mercado");
    }

    @Test
    void archivedListedLast() {
        jdbc.sql("update categories set archived_at = now() where id = :id")
                .param("id", ledger.categoryOf(workspaceId, "EXPENSE", "Pets"))
                .update();

        var result = categories(userId, "EXPENSE");

        assertThat(names(result)).hasSize(13).endsWith("Pets");
    }

    @Test
    void viewerCanRead() {
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(workspaceId, viewerId, Role.VIEWER);

        var result = categories(viewerId, "EXPENSE");

        assertThat(result).hasStatusOk();
        assertThat(names(result)).containsExactlyElementsOf(DEFAULT_EXPENSES);
    }

    @Test
    void kindIsRequired() {
        var missing = mvc.get()
                .uri("/v1/workspaces/" + workspaceId + "/categories")
                .with(TestUsers.signedIn(userId, null))
                .exchange();
        var transfer = categories(userId, "TRANSFER");

        assertThat(missing).hasStatus(400);
        assertThat(missing).bodyJson().extractingPath("$.code").isEqualTo("request.malformed");
        assertThat(transfer).hasStatus(400);
        assertThat(transfer).bodyJson().extractingPath("$.code").isEqualTo("request.malformed");
    }

    @Test
    void listsArchivedAfterActive() {
        send("POST", "/categories/" + ledger.categoryOf(workspaceId, "EXPENSE", "Pets") + "/archive", null, userId);

        var result = categories(userId, "EXPENSE");

        assertThat(names(result)).hasSize(13).endsWith("Pets");
        assertThat(names(result).subList(0, 12))
                .containsExactlyElementsOf(DEFAULT_EXPENSES.stream()
                        .filter(name -> !name.equals("Pets"))
                        .toList());
        assertThat(result).bodyJson().extractingPath("$[12].archivedAt").isNotNull();
        assertThat(result).bodyJson().extractingPath("$[0].archivedAt").isNull();
    }

    @Test
    void createsTopLevelWithColorAndIcon() {
        var created = create("Academia", "EXPENSE", "🏋️", "GREEN", null);

        assertThat(created).hasStatus(201);
        String id = JsonPath.read(text(created), "$.id");
        var listed = categories(userId, "EXPENSE");
        assertThat(names(listed)).contains("Academia");
        List<Object> colors = JsonPath.read(text(listed), "$[?(@.id=='" + id + "')].color");
        List<Object> parents = JsonPath.read(text(listed), "$[?(@.id=='" + id + "')].parentId");
        assertThat(colors).containsExactly("GREEN");
        assertThat(parents).containsExactly((Object) null);
    }

    @Test
    void createsSubcategory() {
        UUID mercado = ledger.categoryOf(workspaceId, "EXPENSE", "Mercado");

        var created = create("Feira", "EXPENSE", null, null, mercado);

        assertThat(created).hasStatus(201);
        assertThat(created).bodyJson().extractingPath("$.parentId").isEqualTo(mercado.toString());
    }

    @Test
    void rejectsGrandchild() {
        UUID feira = feiraUnderMercado();

        assertProblem(create("Neta", "EXPENSE", null, null, feira), 422, "category.parent-invalid");
    }

    @Test
    void rejectsParentOfOtherKind() {
        UUID salario = ledger.categoryOf(workspaceId, "INCOME", "Salário");

        assertProblem(create("Extra", "EXPENSE", null, null, salario), 422, "category.parent-invalid");
    }

    @Test
    void rejectsTransferKind() {
        assertProblem(create("Mover", "TRANSFER", null, null, null), 422, "category.kind-invalid");
    }

    @Test
    void rejectsUnknownColor() {
        assertProblem(create("Preta", "EXPENSE", null, "BLACK", null), 400, "request.malformed");
    }

    @Test
    void rejectsDuplicateSibling() {
        UUID mercado = ledger.categoryOf(workspaceId, "EXPENSE", "Mercado");
        UUID fora = ledger.categoryOf(workspaceId, "EXPENSE", "Alimentação fora");
        assertThat(create("Feira", "EXPENSE", null, null, mercado)).hasStatus(201);

        assertProblem(create("Feira", "EXPENSE", null, null, mercado), 409, "category.name-taken");
        assertThat(create("Feira", "EXPENSE", null, null, fora)).hasStatus(201);
    }

    @Test
    void renamesAndRecolors() {
        UUID pets = ledger.categoryOf(workspaceId, "EXPENSE", "Pets");

        var result = send("PATCH", "/categories/" + pets, "{\"name\":\"Bichos\",\"color\":\"PINK\"}", userId);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Bichos");
        assertThat(result).bodyJson().extractingPath("$.color").isEqualTo("PINK");
        assertThat(result).bodyJson().extractingPath("$.icon").isEqualTo(iconOf(pets));
    }

    @Test
    void archivesParentWithChildren() {
        UUID mercado = ledger.categoryOf(workspaceId, "EXPENSE", "Mercado");
        UUID feira = feiraUnderMercado();

        var result = send("POST", "/categories/" + mercado + "/archive", null, userId);

        assertThat(result).hasStatusOk();
        assertThat(archivedAt(mercado)).isNotEqualTo("active").isEqualTo(archivedAt(feira));
        String first = archivedAt(mercado);
        assertThat(send("POST", "/categories/" + mercado + "/archive", null, userId))
                .hasStatusOk();
        assertThat(archivedAt(mercado)).isEqualTo(first);
    }

    @Test
    void unarchivesParentWithChildren() {
        UUID mercado = ledger.categoryOf(workspaceId, "EXPENSE", "Mercado");
        UUID feira = feiraUnderMercado();
        send("POST", "/categories/" + mercado + "/archive", null, userId);

        var result = send("POST", "/categories/" + mercado + "/unarchive", null, userId);

        assertThat(result).hasStatusOk();
        assertThat(archivedAt(mercado)).isEqualTo("active");
        assertThat(archivedAt(feira)).isEqualTo("active");
    }

    @Test
    void childNeedsActiveParent() {
        UUID mercado = ledger.categoryOf(workspaceId, "EXPENSE", "Mercado");
        UUID feira = feiraUnderMercado();
        send("POST", "/categories/" + mercado + "/archive", null, userId);

        assertProblem(
                send("POST", "/categories/" + feira + "/unarchive", null, userId), 422, "category.parent-archived");
    }

    @Test
    void keepsOneActiveTopLevel() {
        List<String> incomes = List.of("Salário", "Renda extra", "Rendimentos", "Reembolsos", "Presentes", "Outros");
        for (int i = 0; i < 5; i++) {
            UUID id = ledger.categoryOf(workspaceId, "INCOME", incomes.get(i));
            assertThat(send("POST", "/categories/" + id + "/archive", null, userId))
                    .hasStatusOk();
        }

        UUID last = ledger.categoryOf(workspaceId, "INCOME", incomes.get(5));
        assertProblem(send("POST", "/categories/" + last + "/archive", null, userId), 422, "category.last-active");
    }

    @Test
    void viewerCannotWrite() {
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(workspaceId, viewerId, Role.VIEWER);
        UUID pets = ledger.categoryOf(workspaceId, "EXPENSE", "Pets");
        String body = "{\"name\":\"Nova\",\"kind\":\"EXPENSE\"}";

        assertProblem(send("POST", "/categories", body, viewerId), 403, "auth.forbidden");
        assertProblem(send("PATCH", "/categories/" + pets, "{\"name\":\"Outra\"}", viewerId), 403, "auth.forbidden");
        assertProblem(send("POST", "/categories/" + pets + "/archive", null, viewerId), 403, "auth.forbidden");
        assertProblem(send("POST", "/categories/" + pets + "/unarchive", null, viewerId), 403, "auth.forbidden");
    }

    private UUID feiraUnderMercado() {
        var created = create("Feira", "EXPENSE", null, null, ledger.categoryOf(workspaceId, "EXPENSE", "Mercado"));
        assertThat(created).hasStatus(201);
        return UUID.fromString(JsonPath.read(text(created), "$.id"));
    }

    private String iconOf(UUID categoryId) {
        return jdbc.sql("select icon from categories where id = :id")
                .param("id", categoryId)
                .query(String.class)
                .single();
    }

    /** The archived timestamp as text, or "active". */
    private String archivedAt(UUID categoryId) {
        return jdbc.sql("select coalesce(cast(archived_at as text), 'active') from categories where id = :id")
                .param("id", categoryId)
                .query(String.class)
                .single();
    }

    private MvcTestResult create(String name, String kind, String icon, String color, UUID parentId) {
        String body = "{\"name\":%s,\"kind\":%s,\"icon\":%s,\"color\":%s,\"parentId\":%s}"
                .formatted(
                        quoted(name),
                        quoted(kind),
                        quoted(icon),
                        quoted(color),
                        quoted(parentId == null ? null : parentId.toString()));
        return send("POST", "/categories", body, userId);
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private MvcTestResult send(String method, String route, String body, UUID callerId) {
        var request = method.equals("PATCH") ? mvc.patch() : mvc.post();
        request.uri("/v1/workspaces/" + workspaceId + route)
                .with(TestUsers.signedIn(callerId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(callerId, null)));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    private static String text(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertProblem(MvcTestResult result, int status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private void expense(String category, LocalDate on) {
        ledger.expense(workspaceId, membershipId, ledger.categoryOf(workspaceId, "EXPENSE", category), "10.00", on);
    }

    private MvcTestResult categories(UUID callerId, String kind) {
        return mvc.get()
                .uri("/v1/workspaces/" + workspaceId + "/categories?kind=" + kind)
                .with(TestUsers.signedIn(callerId, null))
                .exchange();
    }

    private static List<String> names(MvcTestResult result) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$[*].name");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-16T02:00:00Z"), ZoneOffset.UTC);
        }
    }
}

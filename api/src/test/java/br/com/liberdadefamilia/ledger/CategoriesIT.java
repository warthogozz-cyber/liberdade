package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
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
import java.util.List;
import java.util.Map;
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

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        assertThat(names(result)).containsExactlyElementsOf(DEFAULT_EXPENSES);
        assertThat(result)
                .bodyJson()
                .extractingPath("$[0]")
                .isEqualTo(Map.of(
                        "id",
                                ledger.categoryOf(workspaceId, "EXPENSE", "Mercado")
                                        .toString(),
                        "name", "Mercado",
                        "kind", "EXPENSE",
                        "icon", "🛒"));
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
    void archivedHidden() {
        jdbc.sql("update categories set archived_at = now() where id = :id")
                .param("id", ledger.categoryOf(workspaceId, "EXPENSE", "Pets"))
                .update();

        var result = categories(userId, "EXPENSE");

        assertThat(names(result)).hasSize(12).doesNotContain("Pets");
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

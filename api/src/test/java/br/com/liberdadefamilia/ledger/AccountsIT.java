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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountsIT {

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

    private UUID anaId;
    private UUID workspaceId;
    private UUID walletId;

    @BeforeEach
    void family() {
        anaId = users.create("Ana Teste", "ana@example.com");
        workspaceId = workspaces.createFor(anaId);
        walletId = ledger.walletOf(workspaceId);
    }

    @Test
    void listsActiveFirstThenArchived() {
        insert("Banco", "CHECKING", "0", false);
        insert("Antiga", "SAVINGS", "0", true);

        var result = send("GET", "/accounts", null, anaId);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[*].name").isEqualTo(List.of("Banco", "Carteira", "Antiga"));
        assertThat(result).bodyJson().extractingPath("$[0].archivedAt").isNull();
        assertThat(result).bodyJson().extractingPath("$[1].archivedAt").isNull();
        assertThat(result).bodyJson().extractingPath("$[2].archivedAt").isNotNull();
    }

    @Test
    void createsAccountWithNegativeInitialBalance() {
        var result = create("Cartão", "CREDIT_CARD", "-350.00", "BRL");

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.initialBalance.amount").isEqualTo("-350.00");
        assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("-350.00");
        assertThat(result).bodyJson().extractingPath("$.currency").isEqualTo("BRL");
        assertThat(result).bodyJson().extractingPath("$.archivedAt").isNull();
    }

    @Test
    void rejectsDuplicateActiveName() {
        assertThat(create("Cartão", "CREDIT_CARD", "0", "BRL")).hasStatus(201);

        assertProblem(create(" Cartão ", "CREDIT_CARD", "0", "BRL"), 409, "account.name-taken");
    }

    @Test
    void allowsNameOfArchivedAccount() {
        String id = idOf(create("Cartão", "CREDIT_CARD", "0", "BRL"));
        assertThat(send("POST", "/accounts/" + id + "/archive", null, anaId)).hasStatusOk();

        assertThat(create("Cartão", "CREDIT_CARD", "0", "BRL")).hasStatus(201);
    }

    @Test
    void rejectsOtherCurrency() {
        assertProblem(create("Dólar", "CASH", "10.00", "USD"), 422, "account.currency-unsupported");
    }

    @Test
    void rejectsTooManyDecimals() {
        // The shared Money deserializer refuses this before validation runs, as it does for transactions.
        assertProblem(create("Cofre", "CASH", "10.001", "BRL"), 400, "request.malformed");
    }

    @Test
    void rejectsOwnerFromOtherFamily() {
        UUID biaId = users.create("Bia Teste", "bia@example.com");
        UUID biaWorkspace = workspaces.createFor(biaId);
        UUID biaMembership = workspaces.membershipOf(biaWorkspace, biaId);

        var result = send("POST", "/accounts", """
                {"name":"Banco","type":"CHECKING","initialBalance":{"amount":"0.00","currency":"BRL"},
                 "ownerMembershipId":"%s"}""".formatted(biaMembership), anaId);

        assertProblem(result, 422, "account.owner-not-found");
    }

    @Test
    void renamesAndChangesType() {
        String id = idOf(create("Banco", "CHECKING", "5.00", "BRL"));

        var result = send("PATCH", "/accounts/" + id, "{\"name\":\" Poupança \",\"type\":\"SAVINGS\"}", anaId);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Poupança");
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("SAVINGS");
        assertThat(result).bodyJson().extractingPath("$.initialBalance.amount").isEqualTo("5.00");
        assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("5.00");
    }

    @Test
    void adjustsBalanceByHand() {
        jdbc.sql("update accounts set initial_balance = 100 where id = :id")
                .param("id", walletId)
                .update();
        ledger.expense(
                workspaceId,
                workspaces.membershipOf(workspaceId, anaId),
                ledger.categoryOf(workspaceId, "EXPENSE", "Mercado"),
                "30.00",
                LocalDate.of(2026, 11, 2));

        var result = send(
                "PATCH", "/accounts/" + walletId, "{\"balance\":{\"amount\":\"1000.00\",\"currency\":\"BRL\"}}", anaId);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.balance.amount").isEqualTo("1000.00");
        assertThat(result).bodyJson().extractingPath("$.initialBalance.amount").isEqualTo("1030.00");
    }

    @Test
    void rejectsBothBalances() {
        var result = send("PATCH", "/accounts/" + walletId, """
                {"initialBalance":{"amount":"1.00","currency":"BRL"},"balance":{"amount":"2.00","currency":"BRL"}}""", anaId);

        assertProblem(result, 422, "request.invalid");
    }

    @Test
    void rejectsEmptyPatch() {
        assertProblem(send("PATCH", "/accounts/" + walletId, "{}", anaId), 422, "request.invalid");
    }

    @Test
    void archiveIsIdempotent() {
        String id = idOf(create("Banco", "CHECKING", "0", "BRL"));

        var first = send("POST", "/accounts/" + id + "/archive", null, anaId);
        var second = send("POST", "/accounts/" + id + "/archive", null, anaId);

        assertThat(first).hasStatusOk();
        assertThat(second).hasStatusOk();
        assertThat(archivedAt(second)).isNotNull().isEqualTo(archivedAt(first));
    }

    @Test
    void cannotArchiveLastActive() {
        var result = send("POST", "/accounts/" + walletId + "/archive", null, anaId);

        assertProblem(result, 422, "account.last-active");
        assertThat(jdbc.sql("select archived_at is null from accounts where id = :id")
                        .param("id", walletId)
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    @Test
    void unarchiveFailsWhenNameTaken() {
        String first = idOf(create("Banco", "CHECKING", "0", "BRL"));
        assertThat(send("POST", "/accounts/" + first + "/archive", null, anaId)).hasStatusOk();
        assertThat(create("Banco", "CHECKING", "0", "BRL")).hasStatus(201);

        assertProblem(send("POST", "/accounts/" + first + "/unarchive", null, anaId), 409, "account.name-taken");
    }

    @Test
    void unarchiveIsIdempotent() {
        var result = send("POST", "/accounts/" + walletId + "/unarchive", null, anaId);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.archivedAt").isNull();
    }

    @Test
    void unknownAccountIs404() {
        assertProblem(
                send("PATCH", "/accounts/" + UUID.randomUUID(), "{\"name\":\"Outra\"}", anaId),
                404,
                "account.not-found");
    }

    @Test
    void viewerCannotWrite() {
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(workspaceId, viewerId, Role.VIEWER);
        String body =
                "{\"name\":\"Banco\",\"type\":\"CHECKING\",\"initialBalance\":{\"amount\":\"0\",\"currency\":\"BRL\"}}";

        assertProblem(send("POST", "/accounts", body, viewerId), 403, "auth.forbidden");
        assertProblem(send("PATCH", "/accounts/" + walletId, "{\"name\":\"Outra\"}", viewerId), 403, "auth.forbidden");
        assertProblem(send("POST", "/accounts/" + walletId + "/archive", null, viewerId), 403, "auth.forbidden");
        assertProblem(send("POST", "/accounts/" + walletId + "/unarchive", null, viewerId), 403, "auth.forbidden");
        assertThat(send("GET", "/accounts", null, viewerId)).hasStatusOk();
    }

    @Test
    void setOwner() {
        UUID adultId = users.create("Bia Teste", "bia@example.com");
        UUID adultMembership = workspaces.addMember(workspaceId, adultId, Role.ADULT);

        var set = send("PUT", "/accounts/" + walletId + "/owner", ownerBody(adultMembership), anaId);
        assertThat(set).hasStatusOk();
        assertThat(set).bodyJson().extractingPath("$.ownerMembershipId").isEqualTo(adultMembership.toString());

        var cleared = send("PUT", "/accounts/" + walletId + "/owner", "{\"membershipId\":null}", anaId);
        assertThat(cleared).hasStatusOk();
        assertThat(cleared).bodyJson().extractingPath("$.ownerMembershipId").isNull();
    }

    @Test
    void setOwnerRejectsInactive() {
        UUID removedUser = users.create("Bia Teste", "bia@example.com");
        UUID removed = workspaces.addMember(workspaceId, removedUser, Role.ADULT);
        jdbc.sql("update memberships set status = 'REMOVED' where id = :id")
                .param("id", removed)
                .update();

        var result = send("PUT", "/accounts/" + walletId + "/owner", ownerBody(removed), anaId);

        assertProblem(result, 422, "account.owner-not-found");
    }

    @Test
    void setOwnerOnUnknownAccountIs404() {
        assertProblem(
                send("PUT", "/accounts/" + UUID.randomUUID() + "/owner", "{\"membershipId\":null}", anaId),
                404,
                "account.not-found");
    }

    @Test
    void viewerCannotSetOwner() {
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(workspaceId, viewerId, Role.VIEWER);

        var result = send("PUT", "/accounts/" + walletId + "/owner", "{\"membershipId\":null}", viewerId);

        assertProblem(result, 403, "auth.forbidden");
    }

    private static String ownerBody(UUID membershipId) {
        return "{\"membershipId\":\"%s\"}".formatted(membershipId);
    }

    private void insert(String name, String type, String initial, boolean archived) {
        jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, archived_at)
                        values (:workspaceId, :name, :type, 'BRL', cast(:initial as numeric),
                                case when :archived then now() end)""")
                .param("workspaceId", workspaceId)
                .param("name", name)
                .param("type", type)
                .param("initial", initial)
                .param("archived", archived)
                .update();
    }

    private MvcTestResult create(String name, String type, String amount, String currency) {
        return send("POST", "/accounts", """
                {"name":"%s","type":"%s","initialBalance":{"amount":"%s","currency":"%s"}}""".formatted(name, type, amount, currency), anaId);
    }

    private MvcTestResult send(String method, String route, String body, UUID userId) {
        var request = switch (method) {
            case "GET" -> mvc.get();
            case "PATCH" -> mvc.patch();
            case "PUT" -> mvc.put();
            default -> mvc.post();
        };
        request.uri("/v1/workspaces/" + workspaceId + route).with(TestUsers.signedIn(userId, null));
        if (!method.equals("GET")) {
            request.with(CsrfSession.start(mvc, TestUsers.signedIn(userId, null)));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    private static String idOf(MvcTestResult result) {
        return JsonPath.read(text(result), "$.id");
    }

    private static String archivedAt(MvcTestResult result) {
        return JsonPath.read(text(result), "$.archivedAt");
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
}

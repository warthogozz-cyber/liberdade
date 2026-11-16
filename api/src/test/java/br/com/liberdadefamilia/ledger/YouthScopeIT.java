package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * A YOUTH sees and records only in the accounts they own (D13), always as themself (D15). What is out of their reach
 * answers exactly as what does not exist: the same status, code and body.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class YouthScopeIT {

    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();

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

    private UUID ana;
    private UUID leo;
    private UUID ws;
    private UUID anaMembership;
    private UUID leoMembership;
    private UUID wallet;
    private UUID corrente;
    private UUID mercado;

    /** Dona Ana, the youth Leo with his wallet, and the family's "Corrente". */
    @BeforeEach
    void family() {
        ana = users.create("Ana Teste", "ana@example.com");
        leo = users.create("Leo Teste", "leo@example.com");
        ws = workspaces.createFor(ana);
        join(ana, ws, leo, "Leo");
        anaMembership = workspaces.membershipOf(ws, ana);
        leoMembership = workspaces.membershipOf(ws, leo);
        wallet = jdbc.sql("select id from accounts where workspace_id = :ws and owner_membership_id = :m")
                .param("ws", ws)
                .param("m", leoMembership)
                .query(UUID.class)
                .single();
        corrente = jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance)
                        values (:ws, 'Corrente', 'CHECKING', 'BRL', 1000)
                        returning id""").param("ws", ws).query(UUID.class).single();
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
    }

    @Test
    void youthSeesOnlyWallet() {
        var asLeo = get(leo, "/accounts");
        var asAna = get(ana, "/accounts");

        assertThat(asLeo).hasStatusOk();
        assertThat(asLeo).bodyJson().extractingPath("$[*].name").asArray().containsExactly("Carteira de Leo");
        assertThat(asAna)
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactlyInAnyOrder("Carteira", "Carteira de Leo", "Corrente");
    }

    /** The allowance leaves a family account and enters the wallet: the youth sees it, and so does Dona. */
    @Test
    void youthSeesAllowanceTransfer() {
        UUID allowance = allowance();

        assertThat(listedIds(leo)).containsExactly(allowance.toString());
        assertThat(listedIds(ana)).contains(allowance.toString());
        assertThat(get(leo, "/transactions/" + allowance)).hasStatusOk();
        assertThat(get(leo, "/accounts"))
                .bodyJson()
                .extractingPath("$[0].balance.amount")
                .isEqualTo("50.00");
    }

    @Test
    void youthDoesNotSeeFamilyExpenses() {
        UUID allowance = allowance();
        UUID familyExpense = record(ana, expense(corrente, "300.00", null));

        assertThat(listedIds(ana)).contains(familyExpense.toString());
        assertThat(listedIds(leo)).containsExactly(allowance.toString());
        assertThat(ids(get(leo, "/transactions?month=2026-11&accountId=" + corrente)))
                .containsExactly(allowance.toString());
        assertThat(ids(get(leo, "/transactions?month=2026-11&categoryId=" + mercado)))
                .isEmpty();
    }

    /** Outside the scope, a transaction is as absent as a made-up id, even after it is deleted. */
    @Test
    void youthGetByIdOutsideScopeIs404() {
        UUID familyExpense = record(ana, expense(corrente, "300.00", null));

        var outside = get(leo, "/transactions/" + familyExpense);
        var madeUp = get(leo, "/transactions/" + TestIds.v7());

        assertProblem(outside, 404, "transaction.not-found");
        assertSameAnswer(outside, madeUp);
        assertThat(send(ana, "DELETE", "/transactions/" + familyExpense, null)).hasStatus(204);
        var deleted = get(leo, "/transactions/" + familyExpense);
        assertProblem(deleted, 404, "transaction.not-found");
        assertSameAnswer(deleted, madeUp);
    }

    @Test
    void youthRecordsInWallet() {
        Map<String, Object> body = expense(wallet, "10.00", null);

        var result = post(leo, body);

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.memberId").isEqualTo(leoMembership.toString());
        var asHimself = post(leo, expense(wallet, "5.00", leoMembership));
        assertThat(asHimself).hasStatus(201);
        assertThat(asHimself).bodyJson().extractingPath("$.memberId").isEqualTo(leoMembership.toString());
    }

    @Test
    void youthCannotRecordInFamilyAccount() {
        long before = transactionCount();

        var family = post(leo, expense(corrente, "10.00", null));
        var madeUp = post(leo, expense(TestIds.v7(), "10.00", null));

        assertProblem(family, 422, "transaction.account-not-found");
        assertSameAnswer(family, madeUp);
        assertThat(transactionCount()).isEqualTo(before);
    }

    @Test
    void youthCannotTransferOut() {
        long before = transactionCount();

        var out = post(leo, transfer(wallet, corrente, "10.00"));
        var outToMadeUp = post(leo, transfer(wallet, TestIds.v7(), "10.00"));
        var in = post(leo, transfer(corrente, wallet, "10.00"));
        var inFromMadeUp = post(leo, transfer(TestIds.v7(), wallet, "10.00"));

        assertProblem(out, 422, "transaction.account-not-found");
        assertSameAnswer(out, outToMadeUp);
        assertProblem(in, 422, "transaction.account-not-found");
        assertSameAnswer(in, inFromMadeUp);
        assertThat(transactionCount()).isEqualTo(before);
    }

    @Test
    void youthCannotPickAnotherMember() {
        long before = transactionCount();

        var result = post(leo, expense(wallet, "10.00", anaMembership));

        assertProblem(result, 422, "transaction.member-forbidden");
        // The account is checked first: out of the scope, the member never comes up.
        var outside = post(leo, expense(corrente, "10.00", anaMembership));
        assertSameAnswer(outside, post(leo, expense(TestIds.v7(), "10.00", null)));
        assertThat(transactionCount()).isEqualTo(before);
    }

    @Test
    void youthEditsOnlyOwn() {
        UUID own = record(leo, expense(wallet, "10.00", null));
        UUID allowance = allowance();
        UUID anaInWallet = record(ana, expense(wallet, "3.00", null));
        Map<String, Object> edit = update(0, wallet, "12.00");

        var ownEdit = send(leo, "PATCH", "/transactions/" + own, edit);
        var allowanceEdit = send(leo, "PATCH", "/transactions/" + allowance, transferUpdate(0, corrente, wallet));
        var madeUpEdit = send(leo, "PATCH", "/transactions/" + TestIds.v7(), transferUpdate(0, corrente, wallet));

        assertThat(ownEdit).hasStatusOk();
        assertThat(ownEdit).bodyJson().extractingPath("$.amount.amount").isEqualTo("12.00");
        assertProblem(allowanceEdit, 404, "transaction.not-found");
        assertSameAnswer(allowanceEdit, madeUpEdit);
        // Recorded by Dona in his wallet: in the scope, but not his.
        assertProblem(
                send(leo, "PATCH", "/transactions/" + anaInWallet, update(0, wallet, "4.00")),
                404,
                "transaction.not-found");
        assertProblem(send(leo, "DELETE", "/transactions/" + allowance, null), 404, "transaction.not-found");
        assertProblem(send(leo, "POST", "/transactions/" + allowance + "/restore", null), 404, "transaction.not-found");
        assertProblem(send(leo, "DELETE", "/transactions/" + anaInWallet, null), 404, "transaction.not-found");
        assertThat(jdbc.sql(
                                "select count(*) from transactions where id in (:ids) and deleted_at is null and version = 0")
                        .param("ids", List.of(allowance, anaInWallet))
                        .query(Long.class)
                        .single())
                .isEqualTo(2);

        // Moving his own expense to a family account answers like moving it to a made-up one.
        var toFamily = send(leo, "PATCH", "/transactions/" + own, update(1, corrente, "12.00"));
        var toMadeUp = send(leo, "PATCH", "/transactions/" + own, update(1, TestIds.v7(), "12.00"));
        assertProblem(toFamily, 422, "transaction.account-not-found");
        assertSameAnswer(toFamily, toMadeUp);
        var otherMember = update(1, wallet, "12.00");
        otherMember.put("memberId", anaMembership.toString());
        assertProblem(send(leo, "PATCH", "/transactions/" + own, otherMember), 422, "transaction.member-forbidden");

        assertThat(send(leo, "DELETE", "/transactions/" + own, null)).hasStatus(204);
        assertThat(send(leo, "DELETE", "/transactions/" + own, null)).hasStatus(204);
        assertThat(send(leo, "POST", "/transactions/" + own + "/restore", null)).hasStatusOk();
    }

    /** A deleted family transaction stays out of reach: restoring it is as absent as a made-up id. */
    @Test
    void youthCannotRestoreFamilyTransaction() {
        UUID familyExpense = record(ana, expense(corrente, "300.00", null));
        assertThat(send(ana, "DELETE", "/transactions/" + familyExpense, null)).hasStatus(204);

        var result = send(leo, "POST", "/transactions/" + familyExpense + "/restore", null);

        assertProblem(result, 404, "transaction.not-found");
        assertThat(jdbc.sql("select deleted_at is not null from transactions where id = :id")
                        .param("id", familyExpense)
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    /** Resending the id of a transaction he did not record never hands it back as his. */
    @Test
    void youthResendOfAnotherIdIsAConflict() {
        UUID allowance = allowance();
        UUID familyExpense = record(ana, expense(corrente, "300.00", null));
        Map<String, Object> sameAsAllowance = transfer(corrente, wallet, "50.00");
        sameAsAllowance.put("id", allowance.toString());
        Map<String, Object> walletExpense = expense(wallet, "300.00", null);
        walletExpense.put("id", familyExpense.toString());

        assertProblem(post(leo, sameAsAllowance), 409, "transaction.id-conflict");
        assertProblem(post(leo, walletExpense), 409, "transaction.id-conflict");
    }

    @Test
    void youthDashboardIsScoped() {
        allowance();
        record(leo, expense(wallet, "10.00", null));
        record(ana, expense(corrente, "300.00", null));
        record(ana, income(corrente, "5000.00"));

        var result = get(leo, "/dashboard?month=2026-11");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"income":{"amount":"0.00","currency":"BRL"},
                 "expenses":{"amount":"10.00","currency":"BRL"},
                 "balance":{"amount":"40.00","currency":"BRL"}}""");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.byCategory[*].current.amount")
                .asArray()
                .containsExactly("10.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.dailyBalance[29].balance.amount")
                .isEqualTo("40.00");
    }

    @Test
    void youthCannotWriteCategories() {
        long before = jdbc.sql("select count(*) from categories where workspace_id = :ws")
                .param("ws", ws)
                .query(Long.class)
                .single();

        var result = mvc.post()
                .uri("/v1/workspaces/" + ws + "/categories")
                .with(TestUsers.signedIn(leo, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(leo, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mesada\",\"kind\":\"EXPENSE\"}")
                .exchange();

        assertProblem(result, 403, "auth.forbidden");
        assertThat(jdbc.sql("select count(*) from categories where workspace_id = :ws")
                        .param("ws", ws)
                        .query(Long.class)
                        .single())
                .isEqualTo(before);
    }

    @Test
    void youthReadsMembers() {
        var result = get(leo, "/members");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.length()").isEqualTo(2);
    }

    /** The order of the categories comes from his own spending, not the family's. */
    @Test
    void youthCategoryOrderIgnoresFamilyUsage() {
        List<String> before = ids(get(leo, "/categories?kind=EXPENSE"));
        UUID transporte = ledger.categoryOf(ws, "EXPENSE", "Transporte");
        for (int i = 0; i < 3; i++) {
            Map<String, Object> body = expense(corrente, "5.00", null);
            body.put("categoryId", transporte.toString());
            record(ana, body);
        }

        assertThat(ids(get(ana, "/categories?kind=EXPENSE")).getFirst()).isEqualTo(transporte.toString());
        assertThat(ids(get(leo, "/categories?kind=EXPENSE"))).isEqualTo(before);
    }

    /** A youth who owns no account sees nothing at all: an empty scope is not "every account". */
    @Test
    void youthWithoutAccountsSeesNothing() {
        UUID allowance = allowance();
        jdbc.sql("update accounts set owner_membership_id = null where id = :id")
                .param("id", wallet)
                .update();

        assertThat(get(leo, "/accounts")).bodyJson().isStrictlyEqualTo("[]");
        assertThat(listedIds(leo)).isEmpty();
        assertProblem(get(leo, "/transactions/" + allowance), 404, "transaction.not-found");
        assertThat(get(leo, "/dashboard?month=2026-11")).bodyJson().isLenientlyEqualTo("""
                {"income":{"amount":"0.00"},"expenses":{"amount":"0.00"},"balance":{"amount":"0.00"},
                 "byCategory":[]}""");
        assertProblem(post(leo, expense(wallet, "1.00", null)), 422, "transaction.account-not-found");
    }

    /** An archived wallet is still his: he keeps seeing it and what moved through it. */
    @Test
    void youthKeepsArchivedWallet() {
        UUID allowance = allowance();
        jdbc.sql("update accounts set archived_at = now() where id = :id")
                .param("id", wallet)
                .update();

        assertThat(get(leo, "/accounts"))
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactly("Carteira de Leo");
        assertThat(listedIds(leo)).containsExactly(allowance.toString());
    }

    // --- helpers ---

    /** Dona sends R$ 50,00 from the Corrente to the wallet. */
    private UUID allowance() {
        return record(ana, transfer(corrente, wallet, "50.00"));
    }

    private UUID record(UUID caller, Map<String, Object> body) {
        var result = post(caller, body);
        assertThat(result).hasStatus(201);
        return UUID.fromString((String) body.get("id"));
    }

    private MvcTestResult post(UUID caller, Map<String, Object> body) {
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/transactions")
                .with(TestUsers.signedIn(caller, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(caller, null)))
                .header("Idempotency-Key", (String) body.get("id"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))
                .exchange();
    }

    private Map<String, Object> expense(UUID account, String amount, @Nullable UUID memberId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", TestIds.v7().toString());
        body.put("kind", "EXPENSE");
        body.put("amount", money(amount));
        body.put("occurredOn", "2026-11-03");
        body.put("accountId", account.toString());
        body.put("categoryId", mercado.toString());
        if (memberId != null) {
            body.put("memberId", memberId.toString());
        }
        return body;
    }

    private Map<String, Object> income(UUID account, String amount) {
        Map<String, Object> body = expense(account, amount, null);
        body.put("kind", "INCOME");
        body.put("categoryId", ledger.categoryOf(ws, "INCOME", "Salário").toString());
        return body;
    }

    private static Map<String, Object> transfer(UUID from, UUID to, String amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", TestIds.v7().toString());
        body.put("kind", "TRANSFER");
        body.put("amount", money(amount));
        body.put("occurredOn", "2026-11-02");
        body.put("accountId", from.toString());
        body.put("toAccountId", to.toString());
        return body;
    }

    private Map<String, Object> update(long version, UUID account, String amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("kind", "EXPENSE");
        body.put("amount", money(amount));
        body.put("occurredOn", "2026-11-03");
        body.put("accountId", account.toString());
        body.put("categoryId", mercado.toString());
        return body;
    }

    private static Map<String, Object> transferUpdate(long version, UUID from, UUID to) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("kind", "TRANSFER");
        body.put("amount", money("50.00"));
        body.put("occurredOn", "2026-11-02");
        body.put("accountId", from.toString());
        body.put("toAccountId", to.toString());
        return body;
    }

    private static Map<String, String> money(String amount) {
        return Map.of("amount", amount, "currency", "BRL");
    }

    private MvcTestResult get(UUID caller, String path) {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + path)
                .with(TestUsers.signedIn(caller, null))
                .exchange();
    }

    private MvcTestResult send(UUID caller, String method, String path, @Nullable Map<String, Object> body) {
        var request = switch (method) {
            case "PATCH" -> mvc.patch();
            case "DELETE" -> mvc.delete();
            default -> mvc.post();
        };
        request.uri("/v1/workspaces/" + ws + path)
                .with(TestUsers.signedIn(caller, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(caller, null)));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        }
        return request.exchange();
    }

    private List<String> listedIds(UUID caller) {
        return ids(get(caller, "/transactions?month=2026-11"));
    }

    private static List<String> ids(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        return JsonPath.read(body(result), "$[*].id");
    }

    private long transactionCount() {
        return jdbc.sql("select count(*) from transactions where workspace_id = :ws")
                .param("ws", ws)
                .query(Long.class)
                .single();
    }

    private static void assertProblem(MvcTestResult result, int status, String code) {
        assertThat(result).hasStatus(status).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    /** The same status and the same body, word for word, the request path aside. */
    private static void assertSameAnswer(MvcTestResult actual, MvcTestResult expected) {
        assertThat(actual.getResponse().getStatus())
                .isEqualTo(expected.getResponse().getStatus());
        assertThat(actual.getResponse().getContentType())
                .isEqualTo(expected.getResponse().getContentType());
        assertThat(body(actual)).isEqualTo(body(expected).replace(path(expected), path(actual)));
    }

    private static String path(MvcTestResult result) {
        return result.getRequest().getRequestURI();
    }

    /** Dona invites the youth, with her consent, and he takes the link signed in; the ledger gives him a wallet. */
    private void join(UUID ownerId, UUID workspaceId, UUID personId, String nickname) {
        RequestPostProcessor owner = TestUsers.signedIn(ownerId, null);
        MvcTestResult created = mvc.post()
                .uri("/v1/workspaces/" + workspaceId + "/invitations")
                .with(owner)
                .with(CsrfSession.start(mvc, owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"YOUTH\",\"nickname\":\"" + nickname + "\",\"parentalConsent\":true}")
                .exchange();
        assertThat(created).hasStatus(201);
        String token = JsonPath.read(body(created), "$.token");
        RequestPostProcessor person = TestUsers.signedIn(personId, null);
        MvcTestResult accepted = mvc.post()
                .uri("/v1/invitations/accept")
                .with(person)
                .with(CsrfSession.start(mvc, person))
                .with(request -> {
                    int n = NEXT_ADDRESS.incrementAndGet();
                    request.setRemoteAddr("10.6." + (n / 250) + "." + (n % 250 + 1));
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}")
                .exchange();
        assertThat(accepted).hasStatusOk();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}

package br.com.liberdadefamilia.ledger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransactionsIT {

    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Far above a normal run; only a stuck request gets near it. */
    private static final long TAP_TIMEOUT_SECONDS = 30;

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

    private Family ana;

    @BeforeEach
    void family() {
        ana = family("Ana Teste", "ana@example.com");
    }

    @Test
    void createsExpense() {
        UUID id = TestIds.v7();

        var result = post(ana, id, expense(id, ana));

        assertThat(result).hasStatus(201).hasContentType(MediaType.APPLICATION_JSON);
        assertThat(result).headers().doesNotContainHeader("Location");
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"id":"%s","kind":"EXPENSE","amount":{"amount":"12.34","currency":"BRL"},"occurredOn":"2026-11-02",
                 "accountId":"%s","categoryId":"%s","description":null,"memberId":"%s"}""".formatted(
                        id, ana.walletId(), ana.mercadoId(), ana.membershipId()));
        assertThat(result).bodyJson().extractingPath("$.createdAt").isNotNull();
        assertThat(row(id))
                .containsEntry("workspace_id", ana.workspaceId())
                .containsEntry("member_id", ana.membershipId())
                .containsEntry("created_by", ana.membershipId())
                .containsEntry("currency", "BRL")
                .hasEntrySatisfying(
                        "amount", amount -> assertThat((BigDecimal) amount).isEqualByComparingTo("12.34"));
    }

    @Test
    void resendIsIdempotent() throws Exception {
        UUID id = TestIds.v7();

        var first = post(ana, id, expense(id, ana));
        var second = post(ana, id, expense(id, ana));

        assertThat(first).hasStatus(201);
        assertThat(second).hasStatus(200).hasContentType(MediaType.APPLICATION_JSON);
        assertThat(second).bodyJson().isStrictlyEqualTo(first.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(rowsWithId(id)).isOne();
    }

    @Test
    void sameIdOtherContentIs409() {
        UUID id = TestIds.v7();
        post(ana, id, expense(id, ana));
        Map<String, Object> changed = expense(id, ana);
        changed.put("amount", money("12.35", "BRL"));

        var result = post(ana, id, changed);

        assertThat(result).hasStatus(409).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"code":"transaction.id-conflict","detail":"Este lançamento já existe com outros dados."}""");
        assertThat(row(id))
                .hasEntrySatisfying(
                        "amount", amount -> assertThat((BigDecimal) amount).isEqualByComparingTo("12.34"));
    }

    /** Every field the device chose takes part in "the same transaction", not only the amount. */
    @Test
    void sameIdAnyOtherFieldIs409() {
        UUID id = TestIds.v7();
        assertThat(post(ana, id, expense(id, ana))).hasStatus(201);
        UUID bank = jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency)
                        values (:workspaceId, 'Banco', 'CHECKING', 'BRL')
                        returning id""")
                .param("workspaceId", ana.workspaceId())
                .query(UUID.class)
                .single();
        Map<String, Object> changes = Map.of(
                "occurredOn",
                "2026-11-03",
                "accountId",
                bank.toString(),
                "categoryId",
                ledger.categoryOf(ana.workspaceId(), "EXPENSE", "Lazer").toString(),
                "description",
                "Feira");

        changes.forEach((field, value) -> {
            Map<String, Object> changed = expense(id, ana);
            changed.put(field, value);

            var result = post(ana, id, changed);

            assertThat(result).as(field).hasStatus(409);
            assertThat(result).as(field).bodyJson().extractingPath("$.code").isEqualTo("transaction.id-conflict");
        });
        assertThat(rowsWithId(id)).isOne();
    }

    @Test
    void sameIdOtherFamilyIs409() {
        Family bia = family("Bia Teste", "bia@example.com");
        UUID id = TestIds.v7();
        post(ana, id, expense(id, ana));

        var result = post(bia, id, expense(id, bia));

        assertThat(result).hasStatus(409).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.id-conflict");
        assertThat(result)
                .bodyText()
                .doesNotContain(ana.workspaceId().toString())
                .doesNotContain(ana.walletId().toString())
                .doesNotContain(ana.mercadoId().toString())
                .doesNotContain(ana.membershipId().toString())
                .doesNotContain("amount");
        assertThat(rowsWithId(id)).isOne();
        assertThat(row(id)).containsEntry("workspace_id", ana.workspaceId());
    }

    @Test
    void missingIdempotencyKeyIs400() {
        UUID id = TestIds.v7();

        var missing = post(ana.userId(), ana.workspaceId(), null, expense(id, ana));
        var malformed = post(ana.userId(), ana.workspaceId(), "not-a-uuid", expense(id, ana));

        assertThat(missing).hasStatus(400).hasContentType(PROBLEM_JSON);
        assertThat(missing).bodyJson().extractingPath("$.code").isEqualTo("request.malformed");
        assertThat(malformed).hasStatus(400);
        assertThat(malformed).bodyJson().extractingPath("$.code").isEqualTo("request.malformed");
        assertThat(rowsWithId(id)).isZero();
    }

    @Test
    void keyMustMatchId() {
        UUID id = TestIds.v7();

        var result = post(ana, TestIds.v7(), expense(id, ana));

        assertRejected(result, "transaction.invalid-id", "O identificador do lançamento é inválido.");
        assertThat(rowsWithId(id)).isZero();
    }

    @Test
    void idMustBeV7() {
        UUID id = UUID.randomUUID();

        var result = post(ana, id, expense(id, ana));

        assertRejected(result, "transaction.invalid-id", "O identificador do lançamento é inválido.");
        assertThat(rowsWithId(id)).isZero();
    }

    @Test
    void amountRules() {
        UUID scaleId = TestIds.v7();
        var tooManyDecimals = post(ana, scaleId, expense(scaleId, ana, "12.345"));
        assertThat(tooManyDecimals).as("12.345").hasStatus(400);
        assertThat(tooManyDecimals).bodyJson().extractingPath("$.code").isEqualTo("request.malformed");

        for (String amount : List.of("0.00", "-1.00", "1000000000.00")) {
            UUID id = TestIds.v7();
            var result = post(ana, id, expense(id, ana, amount));
            assertThat(result).as(amount).hasStatus(422).hasContentType(PROBLEM_JSON);
            assertThat(result).as(amount).bodyJson().isLenientlyEqualTo("""
                    {"code":"request.invalid","errors":[{"field":"amount","code":"TransactionAmount"}]}""");
        }

        UUID largestId = TestIds.v7();
        var largest = post(ana, largestId, expense(largestId, ana, "999999999.99"));
        assertThat(largest).hasStatus(201);
        assertThat(largest).bodyJson().extractingPath("$.amount.amount").isEqualTo("999999999.99");
    }

    @Test
    void descriptionRules() {
        UUID tooLongId = TestIds.v7();
        var tooLong = post(ana, tooLongId, describedExpense(tooLongId, "F".repeat(141)));
        UUID longestId = TestIds.v7();
        var longest = post(ana, longestId, describedExpense(longestId, "F".repeat(140)));
        UUID blankId = TestIds.v7();
        var blank = post(ana, blankId, describedExpense(blankId, "   "));
        UUID paddedId = TestIds.v7();
        var padded = post(ana, paddedId, describedExpense(paddedId, " Feira "));

        assertThat(tooLong).hasStatus(422);
        assertThat(tooLong).bodyJson().isLenientlyEqualTo("""
                {"code":"request.invalid","errors":[{"field":"description","code":"Size"}]}""");
        assertThat(longest).hasStatus(201);
        assertThat(blank).hasStatus(201);
        assertThat(blank).bodyJson().extractingPath("$.description").isNull();
        assertThat(row(blankId)).containsEntry("description", null);
        assertThat(padded).hasStatus(201);
        assertThat(padded).bodyJson().extractingPath("$.description").isEqualTo("Feira");
        assertThat(row(paddedId)).containsEntry("description", "Feira");
    }

    @Test
    void missingFieldsAre422() {
        var result = post(ana.userId(), ana.workspaceId(), TestIds.v7().toString(), Map.of());

        assertThat(result).hasStatus(422).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
        List<Map<String, Object>> errors = JsonPath.read(body(result), "$.errors");
        assertThat(errors)
                .containsExactlyInAnyOrder(
                        Map.of("field", "id", "code", "NotNull"),
                        Map.of("field", "kind", "code", "NotNull"),
                        Map.of("field", "amount", "code", "NotNull"),
                        Map.of("field", "occurredOn", "code", "NotNull"),
                        Map.of("field", "accountId", "code", "NotNull"),
                        Map.of("field", "categoryId", "code", "NotNull"));
    }

    @Test
    void accountFromOtherFamilyIs422() {
        Family bia = family("Bia Teste", "bia@example.com");
        UUID id = TestIds.v7();
        Map<String, Object> body = expense(id, ana);
        body.put("accountId", bia.walletId().toString());

        var result = post(ana, id, body);

        assertRejected(result, "transaction.account-not-found", "Conta não encontrada nesta família.");
        assertThat(rowsWithId(id)).isZero();
    }

    @Test
    void archivedAccountIs422() {
        jdbc.sql("update accounts set archived_at = now() where id = :id")
                .param("id", ana.walletId())
                .update();
        UUID id = TestIds.v7();

        var result = post(ana, id, expense(id, ana));

        assertRejected(result, "transaction.account-not-found", "Conta não encontrada nesta família.");
    }

    @Test
    void categoryFromOtherFamilyIs422() {
        Family bia = family("Bia Teste", "bia@example.com");
        UUID id = TestIds.v7();
        Map<String, Object> body = expense(id, ana);
        body.put("categoryId", bia.mercadoId().toString());

        var result = post(ana, id, body);

        assertRejected(result, "transaction.category-not-found", "Categoria não encontrada nesta família.");
        assertThat(rowsWithId(id)).isZero();
    }

    @Test
    void archivedCategoryIs422() {
        jdbc.sql("update categories set archived_at = now() where id = :id")
                .param("id", ana.mercadoId())
                .update();
        UUID id = TestIds.v7();

        var result = post(ana, id, expense(id, ana));

        assertRejected(result, "transaction.category-not-found", "Categoria não encontrada nesta família.");
    }

    @Test
    void incomeCategoryOnExpenseIs422() {
        UUID id = TestIds.v7();
        Map<String, Object> body = expense(id, ana);
        body.put("categoryId", salario(ana).toString());

        var result = post(ana, id, body);

        assertRejected(
                result, "transaction.category-kind-mismatch", "Esta categoria não combina com o tipo do lançamento.");
    }

    @Test
    void incomeIsAccepted() {
        UUID id = TestIds.v7();
        Map<String, Object> body = expense(id, ana);
        body.put("kind", "INCOME");
        body.put("categoryId", salario(ana).toString());

        var result = post(ana, id, body);

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.kind").isEqualTo("INCOME");
        assertThat(row(id)).containsEntry("kind", "INCOME");
    }

    @Test
    void transferIs422() {
        UUID id = TestIds.v7();
        Map<String, Object> body = expense(id, ana);
        body.put("kind", "TRANSFER");

        var result = post(ana, id, body);

        assertRejected(result, "transaction.kind-unsupported", "Transferências ainda não estão disponíveis.");
    }

    @Test
    void currencyMismatchIs422() {
        UUID id = TestIds.v7();

        var result = post(ana, id, expense(id, ana, money("12.34", "USD")));

        assertRejected(result, "transaction.currency-mismatch", "A moeda do valor não é a mesma da conta.");
    }

    @Test
    void viewerCannotPost() {
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(ana.workspaceId(), viewerId, Role.VIEWER);
        UUID id = TestIds.v7();

        var result = post(viewerId, ana.workspaceId(), id.toString(), expense(id, ana));

        assertThat(result).hasStatus(403).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
        assertThat(rowsWithId(id)).isZero();
    }

    @Test
    void listsMonthNewestFirst() {
        UUID early = created(ana, "2026-11-02");
        UUID late = created(ana, "2026-11-10");
        UUID sameDayLater = created(ana, "2026-11-10");
        created(ana, "2026-12-01");
        UUID deleted = created(ana, "2026-11-05");
        jdbc.sql("update transactions set deleted_at = now() where id = :id")
                .param("id", deleted)
                .update();

        var result = list(ana.userId(), "2026-11");

        assertThat(result).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        assertThat(ids(result)).containsExactly(sameDayLater.toString(), late.toString(), early.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$[2]")
                .asMap()
                .containsEntry("kind", "EXPENSE")
                .containsEntry("amount", money("12.34", "BRL"))
                .containsEntry("occurredOn", "2026-11-02")
                .containsEntry("accountId", ana.walletId().toString())
                .containsEntry("categoryId", ana.mercadoId().toString())
                .containsEntry("description", null)
                .containsEntry("memberId", ana.membershipId().toString())
                .containsKey("createdAt");
    }

    @Test
    void viewerCanList() {
        created(ana, "2026-11-02");
        UUID viewerId = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(ana.workspaceId(), viewerId, Role.VIEWER);

        var result = list(viewerId, "2026-11");

        assertThat(result).hasStatusOk();
        assertThat(ids(result)).hasSize(1);
    }

    @Test
    void monthMustBeYearMonth() {
        var missing = mvc.get()
                .uri("/v1/workspaces/" + ana.workspaceId() + "/transactions")
                .with(TestUsers.signedIn(ana.userId(), null))
                .exchange();

        assertThat(missing).hasStatus(400);
        assertThat(missing).bodyJson().extractingPath("$.code").isEqualTo("request.malformed");
        for (String month : List.of("2026-13", "11-2026", "2026-11-02")) {
            var result = list(ana.userId(), month);
            assertThat(result).as(month).hasStatus(400);
            assertThat(result).as(month).bodyJson().extractingPath("$.code").isEqualTo("request.malformed");
        }
    }

    @Test
    void logsNeverShowValues(CapturedOutput output) {
        UUID savedId = TestIds.v7();
        Map<String, Object> saved = expense(savedId, ana, money("43.21", "BRL"));
        saved.put("description", "Feira secreta");
        UUID rejectedId = TestIds.v7();
        Map<String, Object> rejected = expense(rejectedId, ana, money("43.21", "USD"));
        rejected.put("description", "Feira secreta");

        assertThat(post(ana, savedId, saved)).hasStatus(201);
        assertThat(post(ana, rejectedId, rejected)).hasStatus(422);

        assertThat(output.getAll()).doesNotContain("43.21").doesNotContain("Feira secreta");
    }

    /** A double tap on "Salvar": the same session posts the same expense twice at once, and only one is kept. */
    @Test
    void doubleTapCreatesOne() throws Exception {
        UUID id = TestIds.v7();
        String body = JSON.writeValueAsString(expense(id, ana));
        var csrf = CsrfSession.start(mvc, TestUsers.signedIn(ana.userId(), null));
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        Callable<Integer> tap = () -> {
            var request = mvc.post()
                    .uri("/v1/workspaces/" + ana.workspaceId() + "/transactions")
                    .with(TestUsers.signedIn(ana.userId(), null))
                    .with(csrf)
                    .header("Idempotency-Key", id.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body);
            ready.countDown();
            if (!go.await(TAP_TIMEOUT_SECONDS, SECONDS)) {
                throw new TimeoutException("The taps were never released");
            }
            return request.exchange().getResponse().getStatus();
        };

        List<Integer> statuses = new ArrayList<>();
        // Daemon threads that nobody waits for: a stuck tap fails this test instead of hanging the build.
        ExecutorService pool =
                Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        try {
            Future<Integer> first = pool.submit(tap);
            Future<Integer> second = pool.submit(tap);
            assertThat(ready.await(TAP_TIMEOUT_SECONDS, SECONDS))
                    .as("both taps ready")
                    .isTrue();
            go.countDown();
            statuses.add(first.get(TAP_TIMEOUT_SECONDS, SECONDS));
            statuses.add(second.get(TAP_TIMEOUT_SECONDS, SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 200);
        assertThat(rowsWithId(id)).isOne();
    }

    private record Family(UUID userId, UUID workspaceId, UUID membershipId, UUID walletId, UUID mercadoId) {}

    private Family family(String displayName, String email) {
        UUID userId = users.create(displayName, email);
        UUID workspaceId = workspaces.createFor(userId);
        return new Family(
                userId,
                workspaceId,
                workspaces.membershipOf(workspaceId, userId),
                ledger.walletOf(workspaceId),
                ledger.categoryOf(workspaceId, "EXPENSE", "Mercado"));
    }

    private UUID salario(Family family) {
        return ledger.categoryOf(family.workspaceId(), "INCOME", "Salário");
    }

    /** The default expense: 12.34 BRL at the market, from the wallet, on 2026-11-02. */
    private static Map<String, Object> expense(UUID id, Family family) {
        return expense(id, family, money("12.34", "BRL"));
    }

    private static Map<String, Object> expense(UUID id, Family family, String amount) {
        return expense(id, family, money(amount, "BRL"));
    }

    private static Map<String, Object> expense(UUID id, Family family, Map<String, String> amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id.toString());
        body.put("kind", "EXPENSE");
        body.put("amount", amount);
        body.put("occurredOn", "2026-11-02");
        body.put("accountId", family.walletId().toString());
        body.put("categoryId", family.mercadoId().toString());
        return body;
    }

    private Map<String, Object> describedExpense(UUID id, String description) {
        Map<String, Object> body = expense(id, ana);
        body.put("description", description);
        return body;
    }

    private static Map<String, String> money(String amount, String currency) {
        return Map.of("amount", amount, "currency", currency);
    }

    /** Posts the default expense on the given date and returns its id. */
    private UUID created(Family family, String occurredOn) {
        UUID id = TestIds.v7();
        Map<String, Object> body = expense(id, family);
        body.put("occurredOn", occurredOn);
        assertThat(post(family, id, body)).hasStatus(201);
        return id;
    }

    private MvcTestResult post(Family family, UUID idempotencyKey, Map<String, Object> body) {
        return post(family.userId(), family.workspaceId(), idempotencyKey.toString(), body);
    }

    private MvcTestResult post(
            UUID callerId, UUID workspaceId, @Nullable String idempotencyKey, Map<String, Object> body) {
        var request = mvc.post()
                .uri("/v1/workspaces/" + workspaceId + "/transactions")
                .with(TestUsers.signedIn(callerId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(callerId, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return request.exchange();
    }

    private MvcTestResult list(UUID callerId, String month) {
        return mvc.get()
                .uri("/v1/workspaces/" + ana.workspaceId() + "/transactions?month=" + month)
                .with(TestUsers.signedIn(callerId, null))
                .exchange();
    }

    private static void assertRejected(MvcTestResult result, String code, String detail) {
        assertThat(result).hasStatus(422).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"code":"%s","detail":"%s"}""".formatted(code, detail));
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.sql("select * from transactions where id = :id")
                .param("id", id)
                .query()
                .singleRow();
    }

    private long rowsWithId(UUID id) {
        return jdbc.sql("select count(*) from transactions where id = :id")
                .param("id", id)
                .query(Long.class)
                .single();
    }

    private static List<String> ids(MvcTestResult result) {
        return JsonPath.read(body(result), "$[*].id");
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}

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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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

    @Autowired
    Clock clock;

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

    /**
     * A resend repeats what the server already stored, whatever changed since: archiving the account or the category
     * must not turn it into a 422 that the device would then "correct" into a second expense.
     */
    @Test
    void resendAfterArchivingStillReturnsTheRow() throws Exception {
        UUID id = TestIds.v7();
        var first = post(ana, id, expense(id, ana));
        jdbc.sql("update categories set archived_at = now() where id = :id")
                .param("id", ana.mercadoId())
                .update();
        jdbc.sql("update accounts set archived_at = now() where id = :id")
                .param("id", ana.walletId())
                .update();

        var resent = post(ana, id, expense(id, ana));

        assertThat(first).hasStatus(201);
        assertThat(resent).hasStatus(200).hasContentType(MediaType.APPLICATION_JSON);
        assertThat(resent).bodyJson().isStrictlyEqualTo(first.getResponse().getContentAsString(StandardCharsets.UTF_8));
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
                "kind",
                "INCOME",
                "amount",
                money("12.34", "USD"),
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

    /**
     * PostgreSQL text cannot hold U+0000, so it is refused up front instead of failing in the database with a 500 the
     * offline queue would retry forever. Any other character, line breaks and emoji included, stays valid.
     */
    @Test
    void nulInDescriptionIs422() {
        UUID nulId = TestIds.v7();
        UUID multilineId = TestIds.v7();

        var nul = post(ana, nulId, describedExpense(nulId, "Fei\u0000ra"));
        var multiline = post(ana, multilineId, describedExpense(multilineId, "Feira\nde sábado 🍎"));

        assertThat(nul).hasStatus(422).hasContentType(PROBLEM_JSON);
        assertThat(nul).bodyJson().isLenientlyEqualTo("""
                {"code":"request.invalid","errors":[{"field":"description","code":"Pattern"}]}""");
        assertThat(rowsWithId(nulId)).isZero();
        assertThat(multiline).hasStatus(201);
        assertThat(row(multilineId)).containsEntry("description", "Feira\nde sábado 🍎");
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
                        Map.of("field", "accountId", "code", "NotNull"));
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
    void recordsIncome() {
        UUID id = TestIds.v7();
        Map<String, Object> body = expense(id, ana, "5000.00");
        body.put("kind", "INCOME");
        body.put("categoryId", salario(ana).toString());

        var result = post(ana, id, body);

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"kind":"INCOME","amount":{"amount":"5000.00","currency":"BRL"},"toAccountId":null,"version":0}""");
        assertThat(row(id)).containsEntry("kind", "INCOME");
        assertThat(balance(ana, ana.walletId())).isEqualTo("5000.00");
    }

    @Test
    void recordsTransfer() {
        UUID bank = account(ana, "Banco", false);
        UUID id = TestIds.v7();

        var result = post(ana, id, transfer(id, ana, ana.walletId(), bank));

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"kind":"TRANSFER","amount":{"amount":"500.00","currency":"BRL"},
                 "accountId":"%s","toAccountId":"%s","categoryId":null,"version":0}""".formatted(ana.walletId(), bank));
        assertThat(balance(ana, ana.walletId())).isEqualTo("-500.00");
        assertThat(balance(ana, bank)).isEqualTo("500.00");
    }

    @Test
    void rejectsTransferWithoutDestination() {
        UUID id = TestIds.v7();
        Map<String, Object> body = transfer(id, ana, ana.walletId(), account(ana, "Banco", false));
        body.remove("toAccountId");

        assertTransferInvalid(post(ana, id, body));
        assertThat(rowsWithId(id)).isZero();
    }

    @Test
    void rejectsTransferToSameAccount() {
        UUID id = TestIds.v7();

        assertTransferInvalid(post(ana, id, transfer(id, ana, ana.walletId(), ana.walletId())));
    }

    @Test
    void rejectsTransferWithCategory() {
        UUID id = TestIds.v7();
        Map<String, Object> body = transfer(id, ana, ana.walletId(), account(ana, "Banco", false));
        body.put("categoryId", ana.mercadoId().toString());

        assertTransferInvalid(post(ana, id, body));
    }

    @Test
    void rejectsDestinationOnExpense() {
        UUID id = TestIds.v7();
        Map<String, Object> body = expense(id, ana);
        body.put("toAccountId", account(ana, "Banco", false).toString());

        assertTransferInvalid(post(ana, id, body));
    }

    @Test
    void rejectsExpenseWithoutCategory() {
        UUID id = TestIds.v7();
        Map<String, Object> body = expense(id, ana);
        body.remove("categoryId");

        assertRejected(post(ana, id, body), "transaction.category-required", "Escolha uma categoria.");
        assertThat(rowsWithId(id)).isZero();
    }

    @Test
    void rejectsArchivedDestination() {
        UUID id = TestIds.v7();

        var result = post(ana, id, transfer(id, ana, ana.walletId(), account(ana, "Banco", true)));

        assertRejected(result, "transaction.account-not-found", "Conta não encontrada nesta família.");
    }

    @Test
    void acceptsDateBounds() {
        assertThat(postOn("2000-01-01")).hasStatus(201);
        assertThat(postOn(familyToday().plusDays(366).toString())).hasStatus(201);
    }

    @Test
    void rejectsDatesOutOfRange() {
        for (String day : List.of("1999-12-31", familyToday().plusDays(367).toString())) {
            assertRejected(
                    postOn(day), "transaction.date-out-of-range", "Escolha uma data entre 2000 e o próximo ano.");
        }
    }

    /**
     * At 02:00 UTC on the 17th it is still 23:00 on the 16th in Fortaleza, so the last day open is 2027-11-17 (the
     * 16th plus 366 days); a server going by UTC would open 2027-11-18 too.
     */
    @Test
    void usesFamilyTimezoneForToday() {
        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 11, 17));

        assertRejected(
                postOn("2027-11-18"), "transaction.date-out-of-range", "Escolha uma data entre 2000 e o próximo ano.");
        assertThat(postOn("2027-11-17")).hasStatus(201);
    }

    @Test
    void filtersByCategoryIncludingChildren() {
        UUID feira = jdbc.sql("""
                        insert into categories (workspace_id, parent_id, kind, name)
                        values (:workspaceId, :parentId, 'EXPENSE', 'Feira')
                        returning id""")
                .param("workspaceId", ana.workspaceId())
                .param("parentId", ana.mercadoId())
                .query(UUID.class)
                .single();
        UUID atMercado = created(ana, "2026-11-02");
        UUID atFeira = createdIn(ana, feira);
        createdIn(ana, ledger.categoryOf(ana.workspaceId(), "EXPENSE", "Lazer"));

        var result = list(ana.userId(), "2026-11&categoryId=" + ana.mercadoId());

        assertThat(result).hasStatusOk();
        assertThat(ids(result)).containsExactlyInAnyOrder(atMercado.toString(), atFeira.toString());
    }

    @Test
    void filtersByAccountBothDirections() {
        UUID bank = account(ana, "Banco", false);
        UUID transferId = TestIds.v7();
        assertThat(post(ana, transferId, transfer(transferId, ana, ana.walletId(), bank)))
                .hasStatus(201);
        UUID expenseId = created(ana, "2026-11-02");

        var toBank = list(ana.userId(), "2026-11&accountId=" + bank);
        var fromWallet = list(ana.userId(), "2026-11&accountId=" + ana.walletId());

        assertThat(ids(toBank)).containsExactly(transferId.toString());
        assertThat(ids(fromWallet)).containsExactlyInAnyOrder(transferId.toString(), expenseId.toString());
    }

    @Test
    void rejectsMonthOutOfRange() {
        for (String month : List.of("1999-12", "2027-12")) {
            var result = list(ana.userId(), month);
            assertThat(result).as(month).hasStatus(422).hasContentType(PROBLEM_JSON);
            assertThat(result).as(month).bodyJson().isLenientlyEqualTo("""
                    {"code":"month.out-of-range","detail":"Escolha um mês entre janeiro de 2000 e o próximo ano."}""");
        }
        assertThat(list(ana.userId(), "2000-01")).hasStatusOk();
        assertThat(list(ana.userId(), "2027-11")).hasStatusOk();
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

    /**
     * On the same day the entry stored last comes first, whatever its id (D20). The ids here grow against the creation
     * times, so only the creation time explains the order.
     */
    @Test
    void sameDayOrdersByCreatedAtThenId() {
        // Sorted, because two ids from the same millisecond come in random order. For UUIDv7, UUID.compareTo agrees
        // with PostgreSQL's byte order: the top bit of each half is fixed (timestamp and variant).
        List<UUID> ids = Stream.generate(TestIds::v7).limit(2).sorted().toList();
        UUID lower = ids.get(0);
        UUID higher = ids.get(1);
        storedExpense(lower, "2026-11-10T15:00:00Z");
        storedExpense(higher, "2026-11-10T12:00:00Z");

        var result = list(ana.userId(), "2026-11");

        assertThat(ids(result)).containsExactly(lower.toString(), higher.toString());
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

    @Test
    void memberDefaultsToCaller() {
        UUID id = TestIds.v7();

        var result = post(ana, id, expense(id, ana));

        assertThat(result).hasStatus(201);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.memberId")
                .isEqualTo(ana.membershipId().toString());
    }

    @Test
    void adultRecordsForAnotherMember() {
        UUID adultId = users.create("Bia Teste", "bia@example.com");
        UUID adultMembership = workspaces.addMember(ana.workspaceId(), adultId, Role.ADULT);
        UUID id = TestIds.v7();
        Map<String, Object> body = expense(id, ana);
        body.put("memberId", ana.membershipId().toString());

        var result = post(adultId, ana.workspaceId(), id.toString(), body);

        assertThat(result).hasStatus(201);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.memberId")
                .isEqualTo(ana.membershipId().toString());
        assertThat(row(id)).containsEntry("member_id", ana.membershipId()).containsEntry("created_by", adultMembership);
    }

    @Test
    void inactiveMemberRejected() {
        UUID removedUser = users.create("Bia Teste", "bia@example.com");
        UUID removed = workspaces.addMember(ana.workspaceId(), removedUser, Role.ADULT);
        removeMembership(removed);
        Family other = family("Cris Teste", "cris@example.com");

        for (UUID memberId : List.of(removed, other.membershipId())) {
            UUID id = TestIds.v7();
            Map<String, Object> body = expense(id, ana);
            body.put("memberId", memberId.toString());

            var result = post(ana, id, body);

            assertThat(result).as(memberId.toString()).hasStatus(422).hasContentType(PROBLEM_JSON);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.member-not-found");
            assertThat(rowsWithId(id)).isZero();
        }
    }

    @Test
    void editKeepsRemovedMember() {
        UUID removedUser = users.create("Bia Teste", "bia@example.com");
        UUID removed = workspaces.addMember(ana.workspaceId(), removedUser, Role.ADULT);
        UUID id = TestIds.v7();
        Map<String, Object> body = expense(id, ana);
        body.put("memberId", removed.toString());
        assertThat(post(ana, id, body)).hasStatus(201);
        removeMembership(removed);
        Map<String, Object> edit = expense(id, ana, "20.00");
        edit.remove("id");
        edit.put("version", 0);

        var result = patch(id, edit);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.memberId").isEqualTo(removed.toString());
        assertThat(result).bodyJson().extractingPath("$.amount.amount").isEqualTo("20.00");

        Map<String, Object> sameMember = new LinkedHashMap<>(edit);
        sameMember.put("version", 1);
        sameMember.put("memberId", removed.toString());
        assertThat(patch(id, sameMember)).hasStatusOk();

        UUID otherRemovedUser = users.create("Cris Teste", "cris@example.com");
        UUID otherRemoved = workspaces.addMember(ana.workspaceId(), otherRemovedUser, Role.ADULT);
        removeMembership(otherRemoved);
        Family otherFamily = family("Dani Teste", "dani@example.com");
        for (UUID rejectedId : List.of(UUID.randomUUID(), otherRemoved, otherFamily.membershipId())) {
            Map<String, Object> otherMember = new LinkedHashMap<>(edit);
            otherMember.put("version", 2);
            otherMember.put("memberId", rejectedId.toString());
            var rejected = patch(id, otherMember);
            assertThat(rejected).as(rejectedId.toString()).hasStatus(422);
            assertThat(rejected).bodyJson().extractingPath("$.code").isEqualTo("transaction.member-not-found");
            assertThat(row(id)).containsEntry("member_id", removed);
        }
    }

    @Test
    void editChangesMemberToAnotherActiveOne() {
        UUID adultUser = users.create("Bia Teste", "bia@example.com");
        UUID adult = workspaces.addMember(ana.workspaceId(), adultUser, Role.ADULT);
        UUID id = created(ana, "2026-11-02");
        Map<String, Object> edit = expense(id, ana);
        edit.remove("id");
        edit.put("version", 0);
        edit.put("memberId", adult.toString());

        var result = patch(id, edit);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.memberId").isEqualTo(adult.toString());
        assertThat(row(id)).containsEntry("member_id", adult).containsEntry("created_by", ana.membershipId());
    }

    @Test
    void retryWithoutMemberIdIsIdempotent() {
        UUID id = TestIds.v7();

        var first = post(ana, id, expense(id, ana));
        var second = post(ana, id, expense(id, ana));

        assertThat(first).hasStatus(201);
        assertThat(second).hasStatus(200);
        assertThat(rowsWithId(id)).isOne();

        Map<String, Object> other = expense(id, ana);
        UUID adultUser = users.create("Bia Teste", "bia@example.com");
        other.put(
                "memberId",
                workspaces.addMember(ana.workspaceId(), adultUser, Role.ADULT).toString());
        var conflict = post(ana, id, other);
        assertThat(conflict).hasStatus(409);
        assertThat(conflict).bodyJson().extractingPath("$.code").isEqualTo("transaction.id-conflict");
    }

    @Test
    void getById() {
        UUID id = created(ana, "2026-11-02");

        var single = get(ana.userId(), ana.workspaceId(), id);
        var listed = list(ana.userId(), "2026-11");

        assertThat(single).hasStatusOk().hasContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> fromList = JsonPath.read(body(listed), "$[0]");
        assertThat(single).bodyJson().extractingPath("$").asMap().isEqualTo(fromList);
        assertThat(fromList).containsKeys("id", "memberId", "version");
    }

    @Test
    void getDeletedIs409() {
        UUID id = created(ana, "2026-11-02");
        jdbc.sql("update transactions set deleted_at = now() where id = :id")
                .param("id", id)
                .update();

        var result = get(ana.userId(), ana.workspaceId(), id);

        assertThat(result).hasStatus(409).hasContentType(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.deleted");
    }

    @Test
    void getOtherFamilyIs404() {
        Family bia = family("Bia Teste", "bia@example.com");
        UUID id = created(bia, "2026-11-02");

        var other = get(ana.userId(), ana.workspaceId(), id);
        var missing = get(ana.userId(), ana.workspaceId(), TestIds.v7());

        for (var result : List.of(other, missing)) {
            assertThat(result).hasStatus(404).hasContentType(PROBLEM_JSON);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.not-found");
        }
    }

    private void removeMembership(UUID membershipId) {
        jdbc.sql("update memberships set status = 'REMOVED' where id = :id")
                .param("id", membershipId)
                .update();
    }

    private MvcTestResult get(UUID callerId, UUID workspaceId, UUID id) {
        return mvc.get()
                .uri("/v1/workspaces/" + workspaceId + "/transactions/" + id)
                .with(TestUsers.signedIn(callerId, null))
                .exchange();
    }

    private MvcTestResult patch(UUID id, Map<String, Object> body) {
        return mvc.patch()
                .uri("/v1/workspaces/" + ana.workspaceId() + "/transactions/" + id)
                .with(TestUsers.signedIn(ana.userId(), null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(ana.userId(), null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))
                .exchange();
    }

    private record Family(UUID userId, UUID workspaceId, UUID membershipId, UUID walletId, UUID mercadoId) {}

    /** A BRL checking account of the family with no starting balance, archived or not. */
    private UUID account(Family family, String name, boolean archived) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, archived_at)
                        values (:workspaceId, :name, 'CHECKING', 'BRL', case when :archived then now() end)
                        returning id""")
                .param("workspaceId", family.workspaceId())
                .param("name", name)
                .param("archived", archived)
                .query(UUID.class)
                .single();
    }

    /** The account's balance amount, as {@code GET /accounts} shows it. */
    private String balance(Family family, UUID accountId) {
        var result = mvc.get()
                .uri("/v1/workspaces/" + family.workspaceId() + "/accounts")
                .with(TestUsers.signedIn(family.userId(), null))
                .exchange();
        List<String> balances = JsonPath.read(body(result), "$[?(@.id == '%s')].balance.amount".formatted(accountId));
        assertThat(balances).hasSize(1);
        return balances.getFirst();
    }

    /** "Today" for the family: the date in America/Fortaleza at the test clock's instant. */
    private LocalDate familyToday() {
        return LocalDate.now(clock.withZone(ZoneId.of("America/Fortaleza")));
    }

    /** 500.00 BRL from one account to another, with no category. */
    private static Map<String, Object> transfer(UUID id, Family family, UUID from, UUID to) {
        Map<String, Object> body = expense(id, family, "500.00");
        body.put("kind", "TRANSFER");
        body.put("accountId", from.toString());
        body.put("toAccountId", to.toString());
        body.remove("categoryId");
        return body;
    }

    /** Posts Ana's default expense on the given day. */
    private MvcTestResult postOn(String occurredOn) {
        UUID id = TestIds.v7();
        Map<String, Object> body = expense(id, ana);
        body.put("occurredOn", occurredOn);
        return post(ana, id, body);
    }

    /** Posts the default expense in the given category and returns its id. */
    private UUID createdIn(Family family, UUID categoryId) {
        UUID id = TestIds.v7();
        Map<String, Object> body = expense(id, family);
        body.put("categoryId", categoryId.toString());
        assertThat(post(family, id, body)).hasStatus(201);
        return id;
    }

    private static void assertTransferInvalid(MvcTestResult result) {
        assertRejected(
                result,
                "transaction.transfer-invalid",
                "Uma transferência precisa de duas contas diferentes e não leva categoria.");
    }

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

    /** Ana's default expense on 2026-11-10, written by SQL with the given id and creation time. */
    private void storedExpense(UUID id, String createdAt) {
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on,
                             account_id, category_id, member_id, created_by, created_at)
                        values (:id, :workspaceId, 'EXPENSE', 12.34, 'BRL', '2026-11-10',
                                :accountId, :categoryId, :membershipId, :membershipId, :createdAt)""")
                .param("id", id)
                .param("workspaceId", ana.workspaceId())
                .param("accountId", ana.walletId())
                .param("categoryId", ana.mercadoId())
                .param("membershipId", ana.membershipId())
                .param("createdAt", OffsetDateTime.parse(createdAt))
                .update();
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

    /** 02:00 UTC on 2026-11-17: still 23:00 on the 16th in the family's time zone (America/Fortaleza). */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-17T02:00:00Z"), ZoneOffset.UTC);
        }
    }
}

package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.ledger.internal.TransactionPurgeJob;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The confirmed rows of a statement: each line once, planned ones confirmed, the whole batch or nothing. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, ImportIT.Config.class})
@TestPropertySource(properties = "liberdade.billing.enabled=false")
class ImportIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

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
    TestClock clock;

    @Autowired
    TransactionPurgeJob purgeJob;

    @Autowired
    ApplicationEvents events;

    private UUID owner;
    private UUID ws;
    private UUID membership;
    private UUID wallet;
    private UUID mercado;
    private UUID salario;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-23T12:00:00Z"));
        owner = users.create("Dono Teste", null);
        ws = workspaces.createFor(owner);
        membership = workspaces.membershipOf(ws, owner);
        wallet = ledger.walletOf(ws);
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
        salario = ledger.categoryOf(ws, "INCOME", "Salário");
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void createsConfirmedRows() {
        BigDecimal before = balance(wallet);
        List<String> rows = List.of(
                expense(TestIds.v7(), "10.00", day(1), "a"),
                expense(TestIds.v7(), "20.00", day(2), "b"),
                row(TestIds.v7(), "INCOME", "100.00", day(3), "Salario", salario, key("c"), null));

        assertCounts(importRows(wallet, rows), 3, 0, 0);

        List<Stored> stored = stored();
        assertThat(stored).hasSize(3).allSatisfy(row -> {
            assertThat(row.status()).isEqualTo("CONFIRMED");
            assertThat(row.memberId()).isEqualTo(membership);
            assertThat(row.createdBy()).isEqualTo(membership);
            assertThat(row.paymentMethod()).isNull();
        });
        assertThat(stored).extracting(Stored::importKey).containsExactlyInAnyOrder(key("a"), key("b"), key("c"));
        assertThat(balance(wallet)).isEqualByComparingTo(before.add(new BigDecimal("70.00")));
    }

    @Test
    void secondImportCreatesNothing() {
        assertCounts(importRows(wallet, batch(1, 3)), 3, 0, 0);

        assertCounts(importRows(wallet, batch(1, 3)), 0, 0, 3);
        assertThat(stored()).hasSize(3);
    }

    @Test
    void resendCountsAsSkipped() {
        List<String> rows = batch(1, 3);
        assertCounts(importRows(wallet, rows), 3, 0, 0);

        assertCounts(importRows(wallet, rows), 0, 0, 3);
        assertThat(stored()).hasSize(3);
    }

    @Test
    void overlappingPeriods() {
        assertCounts(importRows(wallet, batch(1, 10)), 10, 0, 0);

        assertCounts(importRows(wallet, batch(5, 15)), 5, 0, 6);

        assertThat(stored()).hasSize(15);
        assertThat(stored()).extracting(Stored::importKey).doesNotHaveDuplicates();
    }

    @Test
    void deletedRowDoesNotComeBack() {
        UUID id = TestIds.v7();
        assertCounts(importRows(wallet, List.of(expense(id, "10.00", day(1), "a"))), 1, 0, 0);
        assertThat(send("DELETE", "/transactions/" + id, null)).hasStatus(204);

        assertCounts(importRows(wallet, List.of(expense(TestIds.v7(), "10.00", day(1), "a"))), 0, 0, 1);
        assertThat(stored()).isEmpty();
    }

    @Test
    void purgedRowDoesNotComeBack() {
        UUID id = TestIds.v7();
        assertCounts(importRows(wallet, List.of(expense(id, "10.00", day(1), "a"))), 1, 0, 0);
        assertThat(send("DELETE", "/transactions/" + id, null)).hasStatus(204);
        // Deleted 31 days before the job runs.
        jdbc.sql("update transactions set deleted_at = :at where id = :id")
                .param("at", OffsetDateTime.ofInstant(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC))
                .param("id", id)
                .update();
        purgeJob.purge();
        assertThat(stored()).isEmpty();

        assertCounts(importRows(wallet, List.of(expense(TestIds.v7(), "10.00", day(1), "a"))), 0, 0, 1);
        assertThat(stored()).isEmpty();
    }

    @Test
    void movedRowDoesNotComeBack() {
        UUID corrente = account("Corrente", null);
        UUID id = TestIds.v7();
        assertCounts(importRows(wallet, List.of(expense(id, "10.00", day(1), "a"))), 1, 0, 0);
        String move = """
                {"version":0,"kind":"EXPENSE","amount":{"amount":"10.00","currency":"BRL"},"occurredOn":"%s",
                 "accountId":"%s","categoryId":"%s"}""".formatted(day(1), corrente, mercado);
        assertThat(send("PATCH", "/transactions/" + id, move)).hasStatusOk();

        assertCounts(importRows(wallet, List.of(expense(TestIds.v7(), "10.00", day(1), "a"))), 0, 0, 1);
        assertThat(stored()).hasSize(1);
    }

    @Test
    void confirmsThePlanned() {
        UUID planned = ledger.insertPlanned(ws, wallet, mercado, Money.brl("120.00"), day(10), null, membership);

        assertCounts(
                importRows(
                        wallet,
                        List.of(row(TestIds.v7(), "EXPENSE", "118.50", day(12), "Luz", mercado, key("a"), planned))),
                0,
                1,
                0);

        assertThat(stored()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(planned);
            assertThat(row.status()).isEqualTo("CONFIRMED");
            assertThat(row.amount()).isEqualByComparingTo("118.50");
            assertThat(row.occurredOn()).isEqualTo(day(12));
            assertThat(row.importKey()).isEqualTo(key("a"));
            assertThat(row.memberId()).isEqualTo(membership);
            assertThat(row.version()).isEqualTo(1);
        });
    }

    @Test
    void plannedNoLongerPlannedCreatesNew() {
        UUID planned = ledger.insertPlanned(ws, wallet, mercado, Money.brl("120.00"), day(10), null, membership);
        jdbc.sql("update transactions set deleted_at = now() where id = :id")
                .param("id", planned)
                .update();

        assertCounts(
                importRows(
                        wallet,
                        List.of(row(TestIds.v7(), "EXPENSE", "118.50", day(12), "Luz", mercado, key("a"), planned))),
                1,
                0,
                0);
        assertThat(stored()).singleElement().extracting(Stored::importKey).isEqualTo(key("a"));
        assertThat(statusOf(planned)).isEqualTo("PLANNED");
    }

    @Test
    void plannedWithTakenKeyIsSkipped() {
        assertCounts(importRows(wallet, List.of(expense(TestIds.v7(), "118.50", day(12), "a"))), 1, 0, 0);
        UUID planned = ledger.insertPlanned(ws, wallet, mercado, Money.brl("120.00"), day(10), null, membership);

        assertCounts(
                importRows(
                        wallet,
                        List.of(row(TestIds.v7(), "EXPENSE", "118.50", day(12), "Luz", mercado, key("a"), planned))),
                0,
                0,
                1);
        assertThat(statusOf(planned)).isEqualTo("PLANNED");
        assertThat(versionOf(planned)).isZero();
    }

    @Test
    void plannedIgnoredWithPlanningOff() {
        UUID planned = ledger.insertPlanned(ws, wallet, mercado, Money.brl("120.00"), day(10), null, membership);
        assertThat(switchPlanningOff()).hasStatusOk();

        assertCounts(
                importRows(
                        wallet,
                        List.of(row(TestIds.v7(), "EXPENSE", "118.50", day(12), "Luz", mercado, key("a"), planned))),
                1,
                0,
                0);
        assertThat(statusOf(planned)).isEqualTo("PLANNED");
        assertThat(stored()).hasSize(2);
    }

    @Test
    void rejects501Rows() {
        List<String> rows = batch(1, 501);

        assertProblem(importRows(wallet, rows), 422, "request.invalid");
        assertProblem(importRows(wallet, List.of()), 422, "request.invalid");
        assertThat(stored()).isEmpty();
        assertCounts(importRows(wallet, rows.subList(0, 500)), 500, 0, 0);
    }

    @Test
    void nullRowIsInvalid() {
        assertProblem(importRows(wallet, List.of("null")), 422, "request.invalid");
    }

    @Test
    void badKeyIsInvalid() {
        String row = row(TestIds.v7(), "EXPENSE", "10.00", day(1), "x", mercado, "ABC", null);

        assertProblem(importRows(wallet, List.of(row)), 422, "request.invalid");
    }

    @Test
    void duplicateKeyInBatch() {
        List<String> sameKey =
                List.of(expense(TestIds.v7(), "10.00", day(1), "a"), expense(TestIds.v7(), "11.00", day(2), "a"));
        UUID id = TestIds.v7();
        List<String> sameId = List.of(expense(id, "10.00", day(1), "a"), expense(id, "11.00", day(2), "b"));

        assertProblem(importRows(wallet, sameKey), 422, "import.duplicate-row");
        assertProblem(importRows(wallet, sameId), 422, "import.duplicate-row");
        assertThat(stored()).isEmpty();
    }

    @Test
    void transferRejected() {
        String transfer = row(TestIds.v7(), "TRANSFER", "10.00", day(1), "x", mercado, key("a"), null);

        assertProblem(importRows(wallet, List.of(transfer)), 422, "import.invalid-kind");
        assertThat(stored()).isEmpty();
    }

    @Test
    void notV7IdRejected() {
        String row = row(UUID.randomUUID(), "EXPENSE", "10.00", day(1), "x", mercado, key("a"), null);

        assertProblem(importRows(wallet, List.of(row)), 422, "transaction.invalid-id");
        assertThat(stored()).isEmpty();
    }

    @Test
    void badRowRejectsTheBatch() {
        List<String> rows = List.of(
                expense(TestIds.v7(), "10.00", day(1), "a"),
                row(TestIds.v7(), "EXPENSE", "11.00", day(2), "x", salario, key("b"), null));

        assertProblem(importRows(wallet, rows), 422, "transaction.category-kind-mismatch");
        assertThat(stored()).isEmpty();
    }

    @Test
    void archivedAccountIsNotFound() {
        UUID archived = account("Antiga", Instant.parse("2026-11-01T12:00:00Z"));
        UUID otherFamily = ledger.walletOf(workspaces.createFor(users.create("Outro Dono", null)));
        List<String> rows = List.of(expense(TestIds.v7(), "10.00", day(1), "a"));

        assertProblem(importRows(archived, rows), 404, "account.not-found");
        assertProblem(importRows(otherFamily, rows), 404, "account.not-found");
        assertThat(stored()).isEmpty();
    }

    @Test
    void publishesOneAccountChanged() {
        List<String> rows = batch(1, 3);
        events.clear();

        assertCounts(importRows(wallet, rows), 3, 0, 0);

        assertThat(events.stream(AccountChanged.class)).containsExactly(new AccountChanged(ws, wallet));
        assertThat(events.stream(StatementImported.class))
                .containsExactly(new StatementImported(ws, wallet, owner, 3, 0, 0));

        events.clear();
        assertCounts(importRows(wallet, rows), 0, 0, 3);

        assertThat(events.stream(AccountChanged.class)).isEmpty();
        assertThat(events.stream(StatementImported.class))
                .containsExactly(new StatementImported(ws, wallet, owner, 0, 0, 3));
    }

    @Test
    void logsCarryNoValues(CapturedOutput output) {
        String row = row(TestIds.v7(), "EXPENSE", "987.65", day(7), "Padaria Sigilosa", mercado, key("s"), null);

        assertCounts(importRows(wallet, List.of(row)), 1, 0, 0);

        assertThat(output.getAll())
                .doesNotContain("987.65")
                .doesNotContain("Padaria Sigilosa")
                .doesNotContain(key("s"));
    }

    @Test
    void viewCarriesTheKey() {
        UUID imported = TestIds.v7();
        assertCounts(importRows(wallet, List.of(expense(imported, "10.00", day(1), "a"))), 1, 0, 0);
        ledger.expense(ws, membership, mercado, "5.00", day(2));

        MvcTestResult list = mvc.get()
                .uri("/v1/workspaces/" + ws + "/transactions?month=2026-11")
                .with(TestUsers.signedIn(owner, null))
                .exchange();

        assertThat(list).hasStatusOk();
        String json = body(list);
        assertThat(JsonPath.<List<String>>read(json, "$[?(@.id=='" + imported + "')].importKey"))
                .containsExactly(key("a"));
        assertThat(JsonPath.<List<Object>>read(json, "$[?(@.id!='" + imported + "')].importKey"))
                .hasSize(1)
                .containsOnlyNulls();
    }

    // --- helpers ---

    private static LocalDate day(int day) {
        return LocalDate.of(2026, 11, 1).plusDays(day - 1);
    }

    /** One expense a day, keyed by its day: the same days make the same keys. */
    private List<String> batch(int from, int to) {
        List<String> rows = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            rows.add(expense(TestIds.v7(), "10.00", day(1 + i % 20), "line-" + i));
        }
        return rows;
    }

    private String expense(UUID id, String amount, LocalDate on, String keySeed) {
        return row(id, "EXPENSE", amount, on, "Mercado", mercado, key(keySeed), null);
    }

    private static String row(
            UUID id,
            String kind,
            String amount,
            LocalDate on,
            String description,
            UUID category,
            String importKey,
            @Nullable UUID confirmsPlannedId) {
        return """
                {"id":"%s","kind":"%s","amount":{"amount":"%s","currency":"BRL"},"occurredOn":"%s",
                 "description":"%s","categoryId":"%s","importKey":"%s","confirmsPlannedId":%s}""".formatted(
                        id,
                        kind,
                        amount,
                        on,
                        description,
                        category,
                        importKey,
                        confirmsPlannedId == null ? "null" : "\"" + confirmsPlannedId + "\"");
    }

    private static String key(String seed) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult importRows(UUID account, List<String> rows) {
        return send("POST", "/accounts/" + account + "/imports", "{\"rows\":[" + String.join(",", rows) + "]}");
    }

    private MvcTestResult send(String method, String path, @Nullable String body) {
        var request = switch (method) {
            case "PATCH" -> mvc.patch();
            case "DELETE" -> mvc.delete();
            case "PUT" -> mvc.put();
            default -> mvc.post();
        };
        request.uri("/v1/workspaces/" + ws + path)
                .with(TestUsers.signedIn(owner, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(owner, null)));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    private MvcTestResult switchPlanningOff() {
        return send("PUT", "/modules/planning", "{\"enabled\":false}");
    }

    private static void assertCounts(MvcTestResult result, int created, int confirmed, int skipped) {
        assertThat(result).as(body(result)).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.created").isEqualTo(created);
        assertThat(result).bodyJson().extractingPath("$.confirmed").isEqualTo(confirmed);
        assertThat(result).bodyJson().extractingPath("$.skipped").isEqualTo(skipped);
    }

    private static void assertProblem(MvcTestResult result, int status, String code) {
        assertThat(result).as(body(result)).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }

    private record Stored(
            UUID id,
            String status,
            BigDecimal amount,
            LocalDate occurredOn,
            UUID memberId,
            UUID createdBy,
            @Nullable String paymentMethod,
            @Nullable String importKey,
            long version) {}

    /** The family's transactions that are not deleted. */
    private List<Stored> stored() {
        return jdbc.sql("""
                        select id, status, amount, occurred_on, member_id, created_by, payment_method, import_key,
                               version
                        from transactions
                        where workspace_id = :ws and deleted_at is null""")
                .param("ws", ws)
                .query((rs, n) -> new Stored(
                        rs.getObject("id", UUID.class),
                        rs.getString("status"),
                        rs.getBigDecimal("amount"),
                        rs.getObject("occurred_on", LocalDate.class),
                        rs.getObject("member_id", UUID.class),
                        rs.getObject("created_by", UUID.class),
                        rs.getString("payment_method"),
                        rs.getString("import_key"),
                        rs.getLong("version")))
                .list();
    }

    private String statusOf(UUID id) {
        return jdbc.sql("select status from transactions where id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }

    private long versionOf(UUID id) {
        return jdbc.sql("select version from transactions where id = :id")
                .param("id", id)
                .query(Long.class)
                .single();
    }

    private UUID account(String name, @Nullable Instant archivedAt) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, archived_at)
                        values (:ws, :name, 'CHECKING', 'BRL', :archivedAt) returning id""")
                .param("ws", ws)
                .param("name", name)
                .param("archivedAt", archivedAt == null ? null : OffsetDateTime.ofInstant(archivedAt, ZoneOffset.UTC))
                .query(UUID.class)
                .single();
    }

    private BigDecimal balance(UUID account) {
        MvcTestResult accounts = mvc.get()
                .uri("/v1/workspaces/" + ws + "/accounts")
                .with(TestUsers.signedIn(owner, null))
                .exchange();
        List<String> amounts = JsonPath.read(body(accounts), "$[?(@.id=='" + account + "')].balance.amount");
        return new BigDecimal(amounts.getFirst());
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}

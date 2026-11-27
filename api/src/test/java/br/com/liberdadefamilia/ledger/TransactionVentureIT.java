package br.com.liberdadefamilia.ledger;

import static br.com.liberdadefamilia.workspace.Role.YOUTH;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * The optional venture mark on incomes and expenses: kept on an edit that does not mention it, removed by {@code
 * clearVenture}, refused on a transfer, never shown to the youth, and dropped when the venture is deleted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransactionVentureIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String DETAIL =
            "Esse projeto não existe mais nesta família. Tire o projeto do lançamento e envie de novo.";

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
    private UUID wallet;
    private UUID leoWallet;
    private UUID corrente;
    private UUID mercado;
    private UUID salario;
    private UUID venture;

    @BeforeEach
    void family() {
        ana = users.create("Ana Teste", "ana@example.com");
        leo = users.create("Leo Teste", "leo@example.com");
        ws = workspaces.createFor(ana);
        UUID leoMembership = workspaces.addMember(ws, leo, YOUTH, "Leo");
        wallet = ledger.walletOf(ws);
        leoWallet = account("Carteira de Leo", leoMembership);
        corrente = account("Corrente", null);
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
        salario = ledger.categoryOf(ws, "INCOME", "Salário");
        venture = venture(ws, "ACTIVE");
    }

    @Test
    void createIncomeWithVenture() {
        UUID id = TestIds.v7();

        var created = post(ana, income(id, venture));

        assertThat(created).hasStatus(201);
        assertThat(created).bodyJson().extractingPath("$.ventureId").isEqualTo(venture.toString());
        assertThat(get(ana, "/transactions/" + id))
                .bodyJson()
                .extractingPath("$.ventureId")
                .isEqualTo(venture.toString());
    }

    @Test
    void transferWithVentureIs422() {
        UUID id = TestIds.v7();
        Map<String, Object> body = base(id, "TRANSFER", wallet, null);
        body.put("toAccountId", corrente.toString());
        body.put("ventureId", venture.toString());

        var result = post(ana, body);

        assertRejected(result);
        assertThat(count("select count(*) from transactions where id = :id", id))
                .isZero();
    }

    @Test
    void otherFamilyVentureIs422() {
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID otherWs = workspaces.createFor(bia);
        UUID foreign = venture(otherWs, "ACTIVE");

        var result = post(ana, income(TestIds.v7(), foreign));

        assertRejected(result);
    }

    @Test
    void deletedVentureIs422() {
        UUID gone = venture(ws, "ACTIVE");
        jdbc.sql("delete from ventures where id = :id").param("id", gone).update();

        var result = post(ana, income(TestIds.v7(), gone));

        assertRejected(result);
    }

    @Test
    void closedVentureAccepts() {
        UUID closed = venture(ws, "CLOSED");

        var result = post(ana, income(TestIds.v7(), closed));

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.ventureId").isEqualTo(closed.toString());
    }

    @Test
    void updateWithoutFieldsKeepsVenture() {
        UUID id = TestIds.v7();
        assertThat(post(ana, income(id, venture))).hasStatus(201);

        var edited = patch(ana, id, edit(0, "99.00", null, null));

        assertThat(edited).hasStatusOk();
        assertThat(edited).bodyJson().extractingPath("$.ventureId").isEqualTo(venture.toString());
        assertThat(storedVenture(id)).isEqualTo(venture);
    }

    @Test
    void clearVentureRemovesIt() {
        UUID id = TestIds.v7();
        assertThat(post(ana, income(id, venture))).hasStatus(201);

        var edited = patch(ana, id, edit(0, "12.34", null, true));

        assertThat(edited).hasStatusOk();
        assertThat(edited).bodyJson().extractingPath("$.ventureId").isNull();
        assertThat(storedVenture(id)).isNull();
    }

    @Test
    void updateCanSetAndChangeVenture() {
        UUID id = TestIds.v7();
        assertThat(post(ana, income(id, null))).hasStatus(201);
        UUID other = venture(ws, "PAUSED");

        var set = patch(ana, id, edit(0, "12.34", venture, null));
        var changed = patch(ana, id, edit(1, "12.34", other, null));

        assertThat(set).bodyJson().extractingPath("$.ventureId").isEqualTo(venture.toString());
        assertThat(changed).bodyJson().extractingPath("$.ventureId").isEqualTo(other.toString());
    }

    @Test
    void bothFieldsIs422() {
        UUID id = TestIds.v7();
        assertThat(post(ana, income(id, venture))).hasStatus(201);

        var result = patch(ana, id, edit(0, "12.34", venture, true));

        assertRejected(result);
        assertThat(storedVenture(id)).isEqualTo(venture);
    }

    @Test
    void toTransferWithoutClearIs422() {
        UUID id = TestIds.v7();
        assertThat(post(ana, income(id, venture))).hasStatus(201);
        Map<String, Object> body = edit(0, "12.34", null, null);
        body.put("kind", "TRANSFER");
        body.put("toAccountId", corrente.toString());
        body.remove("categoryId");

        var result = patch(ana, id, body);

        assertRejected(result);
        assertThat(storedVenture(id)).isEqualTo(venture);

        body.put("clearVenture", true);
        assertThat(patch(ana, id, body)).hasStatusOk();
        assertThat(storedVenture(id)).isNull();
    }

    @Test
    void deletingVentureUnmarks() {
        UUID id = TestIds.v7();
        assertThat(post(ana, income(id, venture))).hasStatus(201);

        jdbc.sql("delete from ventures where id = :id").param("id", venture).update();

        var after = get(ana, "/transactions/" + id);
        assertThat(after).hasStatusOk();
        assertThat(after).bodyJson().extractingPath("$.ventureId").isNull();
        assertThat(count("select count(*) from transactions where id = :id", id))
                .isEqualTo(1);
    }

    @Test
    void confirmPlannedKeepsVenture() {
        UUID membership = workspaces.membershipOf(ws, ana);
        UUID id = ledger.insertPlanned(
                ws, wallet, mercado, Money.brl("30.00"), LocalDate.of(2026, 11, 5), null, membership);
        jdbc.sql("update transactions set venture_id = :v where id = :id")
                .param("v", venture)
                .param("id", id)
                .update();

        var confirmed = send(ana, "POST", "/transactions/" + id + "/confirm", "{\"version\":0}");

        assertThat(confirmed).hasStatusOk();
        assertThat(confirmed).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(confirmed).bodyJson().extractingPath("$.ventureId").isEqualTo(venture.toString());
    }

    @Test
    void youthCannotSendVentureFields() {
        Map<String, Object> withVenture = base(TestIds.v7(), "EXPENSE", leoWallet, mercado);
        withVenture.put("ventureId", venture.toString());
        var created = post(leo, withVenture);
        assertRejected(created);

        UUID id = TestIds.v7();
        assertThat(post(leo, base(id, "EXPENSE", leoWallet, mercado))).hasStatus(201);
        var editWithVenture = patch(leo, id, edit(0, "12.34", venture, null, leoWallet, mercado));
        var editWithClear = patch(leo, id, edit(0, "12.34", null, true, leoWallet, mercado));

        assertRejected(editWithVenture);
        assertRejected(editWithClear);
    }

    @Test
    void youthEditKeepsVentureAndHidesIt() {
        UUID id = TestIds.v7();
        assertThat(post(leo, base(id, "EXPENSE", leoWallet, mercado))).hasStatus(201);
        jdbc.sql("update transactions set venture_id = :v where id = :id")
                .param("v", venture)
                .param("id", id)
                .update();

        var edited = patch(leo, id, edit(0, "20.00", null, null, leoWallet, mercado));

        assertThat(edited).hasStatusOk();
        assertThat(edited).bodyJson().extractingPath("$.ventureId").isNull();
        assertThat(storedVenture(id)).isEqualTo(venture);
        var detail = get(leo, "/transactions/" + id);
        assertThat(detail).hasStatusOk();
        assertThat(detail).bodyJson().extractingPath("$.ventureId").isNull();
        var list = get(leo, "/transactions?month=2026-11");
        assertThat((List<?>) JsonPath.read(text(list), "$[?(@.id=='" + id + "')]"))
                .hasSize(1);
        assertThat((List<?>) JsonPath.read(text(list), "$[*].ventureId")).containsOnlyNulls();
        assertThat(text(list)).doesNotContain(venture.toString());
    }

    @Test
    void youthEditToTransferClearsVenture() {
        UUID id = TestIds.v7();
        assertThat(post(leo, base(id, "EXPENSE", leoWallet, mercado))).hasStatus(201);
        jdbc.sql("update transactions set venture_id = :v where id = :id")
                .param("v", venture)
                .param("id", id)
                .update();
        Map<String, Object> body = edit(0, "12.34", null, null, leoWallet, mercado);
        body.put("kind", "TRANSFER");
        UUID piggyBank = account("Cofrinho de Leo", workspaces.membershipOf(ws, leo));
        body.put("toAccountId", piggyBank.toString());
        body.remove("categoryId");

        var edited = patch(leo, id, body);

        assertThat(edited).hasStatusOk();
        assertThat(edited).bodyJson().extractingPath("$.ventureId").isNull();
        assertThat(storedVenture(id)).isNull();
    }

    @Test
    void filterByVenture() {
        UUID other = venture(ws, "ACTIVE");
        UUID marked = TestIds.v7();
        UUID otherMarked = TestIds.v7();
        UUID plain = TestIds.v7();
        assertThat(post(ana, income(marked, venture))).hasStatus(201);
        assertThat(post(ana, income(otherMarked, other))).hasStatus(201);
        assertThat(post(ana, income(plain, null))).hasStatus(201);

        var filtered = get(ana, "/transactions?month=2026-11&ventureId=" + venture);

        assertThat(filtered).hasStatusOk();
        assertThat(JsonPath.<List<String>>read(text(filtered), "$[*].id")).containsExactly(marked.toString());

        UUID youthOwn = TestIds.v7();
        assertThat(post(leo, base(youthOwn, "EXPENSE", leoWallet, mercado))).hasStatus(201);
        var asYouth = get(leo, "/transactions?month=2026-11&ventureId=" + venture);
        assertThat(asYouth).hasStatusOk();
        assertThat(JsonPath.<List<String>>read(text(asYouth), "$[*].id")).containsExactly(youthOwn.toString());
    }

    // --- helpers ---

    private static void assertRejected(MvcTestResult result) {
        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.venture-invalid");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(DETAIL);
    }

    private UUID account(String name, @Nullable UUID owner) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, owner_membership_id)
                        values (:ws, :name, 'CHECKING', 'BRL', 1000, :owner)
                        returning id""")
                .param("ws", ws)
                .param("name", name)
                .param("owner", owner)
                .query(UUID.class)
                .single();
    }

    private UUID venture(UUID workspace, String status) {
        boolean closed = "CLOSED".equals(status);
        return jdbc.sql("""
                        insert into ventures (workspace_id, name, type, status, started_on, closed_on, currency)
                        values (:ws, 'Doces', 'FOOD', :status, date '2026-10-01',
                                case when :closed then date '2026-10-20' end, 'BRL')
                        returning id""")
                .param("ws", workspace)
                .param("status", status)
                .param("closed", closed)
                .query(UUID.class)
                .single();
    }

    private @Nullable UUID storedVenture(UUID id) {
        return jdbc.sql("select venture_id from transactions where id = :id")
                .param("id", id)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    private long count(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(Long.class).single();
    }

    private Map<String, Object> base(UUID id, String kind, UUID account, @Nullable UUID category) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id.toString());
        body.put("kind", kind);
        body.put("amount", Map.of("amount", "12.34", "currency", "BRL"));
        body.put("occurredOn", "2026-11-02");
        body.put("accountId", account.toString());
        if (category != null) {
            body.put("categoryId", category.toString());
        }
        return body;
    }

    private Map<String, Object> income(UUID id, @Nullable UUID ventureId) {
        Map<String, Object> body = base(id, "INCOME", wallet, salario);
        if (ventureId != null) {
            body.put("ventureId", ventureId.toString());
        }
        return body;
    }

    private Map<String, Object> edit(long version, String amount, @Nullable UUID ventureId, @Nullable Boolean clear) {
        return edit(version, amount, ventureId, clear, wallet, salario);
    }

    private Map<String, Object> edit(
            long version,
            String amount,
            @Nullable UUID ventureId,
            @Nullable Boolean clear,
            UUID account,
            UUID category) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("kind", category.equals(salario) ? "INCOME" : "EXPENSE");
        body.put("amount", Map.of("amount", amount, "currency", "BRL"));
        body.put("occurredOn", "2026-11-02");
        body.put("accountId", account.toString());
        body.put("categoryId", category.toString());
        if (ventureId != null) {
            body.put("ventureId", ventureId.toString());
        }
        if (clear != null) {
            body.put("clearVenture", clear);
        }
        return body;
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

    private MvcTestResult patch(UUID caller, UUID id, Map<String, Object> body) {
        return send(caller, "PATCH", "/transactions/" + id, JSON.writeValueAsString(body));
    }

    private MvcTestResult send(UUID caller, String method, String path, String json) {
        var request = "PATCH".equals(method) ? mvc.patch() : mvc.post();
        return request.uri("/v1/workspaces/" + ws + path)
                .with(TestUsers.signedIn(caller, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(caller, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private MvcTestResult get(UUID caller, String path) {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + path)
                .with(TestUsers.signedIn(caller, null))
                .exchange();
    }

    private static String text(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}

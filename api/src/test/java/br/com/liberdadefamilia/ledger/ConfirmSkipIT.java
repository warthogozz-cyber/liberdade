package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

/** Confirming a planned transaction makes it a real one; skipping it deletes it, and "Desfazer" brings it back. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ConfirmSkipIT.Config.class})
class ConfirmSkipIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    private static final LocalDate DUE = LocalDate.of(2026, 11, 25);

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

    private UUID ana;
    private UUID ws;
    private UUID anaMembership;
    private UUID wallet;
    private UUID mercado;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-23T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        anaMembership = workspaces.membershipOf(ws, ana);
        wallet = ledger.walletOf(ws);
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void confirmAsIs() {
        UUID id = planned(wallet, "90.00");
        BigDecimal before = balance(wallet);

        var result = post(ana, "/transactions/" + id + "/confirm", "{\"version\":0}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(result).bodyJson().extractingPath("$.memberId").isEqualTo(anaMembership.toString());
        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.scheduledOn").isEqualTo("2026-11-25");
        assertThat(createdBy(id)).isEqualTo(anaMembership);
        assertThat(balance(wallet)).isEqualByComparingTo(before.subtract(new BigDecimal("90.00")));
    }

    @Test
    void confirmedByAnotherMemberRecordsWhoConfirmed() {
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bia");
        UUID id = planned(wallet, "90.00");

        var result = post(bia, "/transactions/" + id + "/confirm", "{\"version\":0}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.memberId").isEqualTo(biaMembership.toString());
        assertThat(createdBy(id)).isEqualTo(anaMembership);
    }

    @Test
    void confirmWithAdjustments() {
        UUID corrente = account("Corrente", "BRL", null);
        UUID id = planned(wallet, "90.00");

        var result = post(ana, "/transactions/" + id + "/confirm", """
                {"version":0,"amount":{"amount":"187.40","currency":"BRL"},"occurredOn":"2026-11-27",
                 "accountId":"%s"}""".formatted(corrente));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.amount.amount").isEqualTo("187.40");
        assertThat(result).bodyJson().extractingPath("$.occurredOn").isEqualTo("2026-11-27");
        assertThat(result).bodyJson().extractingPath("$.accountId").isEqualTo(corrente.toString());
        assertThat(result).bodyJson().extractingPath("$.scheduledOn").isEqualTo("2026-11-25");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
    }

    @Test
    void confirmWithOtherCurrencyFails() {
        UUID dollars = account("Dólares", "USD", null);
        UUID id = planned(wallet, "90.00");

        var result = post(ana, "/transactions/" + id + "/confirm", """
                {"version":0,"accountId":"%s"}""".formatted(dollars));

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.currency-mismatch");
        assertThat(statusOf(id)).isEqualTo("PLANNED");
    }

    @Test
    void confirmTwiceIsNotPlanned() {
        UUID id = planned(wallet, "90.00");
        assertThat(post(ana, "/transactions/" + id + "/confirm", "{\"version\":0}"))
                .hasStatusOk();

        var result = post(ana, "/transactions/" + id + "/confirm", "{\"version\":1}");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.not-planned");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Este lançamento já foi confirmado.");
    }

    @Test
    void staleVersionConflicts() {
        UUID id = planned(wallet, "90.00");

        var result = post(ana, "/transactions/" + id + "/confirm", "{\"version\":5}");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.version-conflict");
        assertThat(result).bodyJson().extractingPath("$.current.version").isEqualTo(0);
        assertThat(statusOf(id)).isEqualTo("PLANNED");
    }

    @Test
    void confirmAfterSkipIsNotFound() {
        UUID id = planned(wallet, "90.00");
        assertThat(post(ana, "/transactions/" + id + "/skip", "{\"version\":0}"))
                .hasStatus(204);

        var result = post(ana, "/transactions/" + id + "/confirm", "{\"version\":1}");

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.not-found");
    }

    @Test
    void skipAfterConfirmIsNotPlanned() {
        UUID id = planned(wallet, "90.00");
        assertThat(post(ana, "/transactions/" + id + "/confirm", "{\"version\":0}"))
                .hasStatusOk();

        var result = post(ana, "/transactions/" + id + "/skip", "{\"version\":1}");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.not-planned");
        assertThat(statusOf(id)).isEqualTo("CONFIRMED");
        assertThat(deleted(id)).isFalse();
    }

    @Test
    void skipWithStaleVersionConflicts() {
        UUID id = planned(wallet, "90.00");

        var result = post(ana, "/transactions/" + id + "/skip", "{\"version\":3}");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.version-conflict");
        assertThat(deleted(id)).isFalse();
    }

    @Test
    void skipThenRestore() {
        UUID id = planned(wallet, "90.00");

        assertThat(post(ana, "/transactions/" + id + "/skip", "{\"version\":0}"))
                .hasStatus(204);
        assertThat(ids(get(ana, "/transactions?month=2026-11"))).isEmpty();

        var restored = post(ana, "/transactions/" + id + "/restore", null);

        assertThat(restored).hasStatusOk();
        assertThat(restored).bodyJson().extractingPath("$.status").isEqualTo("PLANNED");
        assertThat(restored).bodyJson().extractingPath("$.scheduledOn").isEqualTo("2026-11-25");
        assertThat(ids(get(ana, "/transactions?month=2026-11"))).containsExactly(id.toString());
    }

    @Test
    void youthConfirmsOwnAccount() {
        UUID leo = users.create("Leo Teste", "leo@example.com");
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        UUID leoWallet = account("Carteira de Leo", "BRL", leoMembership);
        UUID id = planned(leoWallet, "5.00");

        var result = post(leo, "/transactions/" + id + "/confirm", "{\"version\":0}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(result).bodyJson().extractingPath("$.memberId").isEqualTo(leoMembership.toString());
    }

    @Test
    void youthSkipsOwnAccount() {
        UUID leo = users.create("Leo Teste", "leo@example.com");
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        UUID leoWallet = account("Carteira de Leo", "BRL", leoMembership);
        UUID id = planned(leoWallet, "5.00");

        assertThat(post(leo, "/transactions/" + id + "/skip", "{\"version\":0}"))
                .hasStatus(204);
        assertThat(deleted(id)).isTrue();
    }

    @Test
    void youthCannotConfirmAdultPlanned() {
        UUID leo = users.create("Leo Teste", "leo@example.com");
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        account("Carteira de Leo", "BRL", leoMembership);
        UUID id = planned(wallet, "90.00");

        var confirm = post(leo, "/transactions/" + id + "/confirm", "{\"version\":0}");
        var skip = post(leo, "/transactions/" + id + "/skip", "{\"version\":0}");

        assertThat(confirm).hasStatus(404);
        assertThat(confirm).bodyJson().extractingPath("$.code").isEqualTo("transaction.not-found");
        assertThat(skip).hasStatus(404);
        assertThat(statusOf(id)).isEqualTo("PLANNED");
        assertThat(deleted(id)).isFalse();
    }

    @Test
    void youthCannotMoveTheConfirmedOneToAnAdultAccount() {
        UUID leo = users.create("Leo Teste", "leo@example.com");
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        UUID leoWallet = account("Carteira de Leo", "BRL", leoMembership);
        UUID id = planned(leoWallet, "5.00");

        var result = post(leo, "/transactions/" + id + "/confirm", """
                {"version":0,"accountId":"%s"}""".formatted(wallet));

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.account-not-found");
        assertThat(statusOf(id)).isEqualTo("PLANNED");
    }

    @Test
    void viewerCannot() {
        UUID vera = users.create("Vera Teste", "vera@example.com");
        workspaces.addMember(ws, vera, Role.VIEWER, "Vera");
        UUID id = planned(wallet, "90.00");

        assertThat(post(vera, "/transactions/" + id + "/confirm", "{\"version\":0}"))
                .hasStatus(403);
        assertThat(post(vera, "/transactions/" + id + "/skip", "{\"version\":0}"))
                .hasStatus(403);
        assertThat(statusOf(id)).isEqualTo("PLANNED");
    }

    @Test
    void moduleOffIs403() {
        UUID id = planned(wallet, "90.00");
        assertThat(switchPlanningOff()).hasStatusOk();

        var confirm = post(ana, "/transactions/" + id + "/confirm", "{\"version\":0}");
        var skip = post(ana, "/transactions/" + id + "/skip", "{\"version\":0}");

        assertThat(confirm).hasStatus(403);
        assertThat(confirm).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
        assertThat(skip).hasStatus(403);
        assertThat(skip).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
        assertThat(statusOf(id)).isEqualTo("PLANNED");
    }

    @Test
    void missingVersionIsInvalid() {
        UUID id = planned(wallet, "90.00");

        var result = post(ana, "/transactions/" + id + "/confirm", "{}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
    }

    private UUID planned(UUID account, String amount) {
        return ledger.insertPlanned(ws, account, mercado, Money.brl(amount), DUE, null, anaMembership);
    }

    private UUID account(String name, String currency, UUID owner) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, owner_membership_id)
                        values (:ws, :name, 'CHECKING', :currency, :owner) returning id""")
                .param("ws", ws)
                .param("name", name)
                .param("currency", currency)
                .param("owner", owner)
                .query(UUID.class)
                .single();
    }

    private String statusOf(UUID id) {
        return jdbc.sql("select status from transactions where id = :id")
                .param("id", id)
                .query(String.class)
                .single();
    }

    private boolean deleted(UUID id) {
        return jdbc.sql("select deleted_at is not null from transactions where id = :id")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    private UUID createdBy(UUID id) {
        return jdbc.sql("select created_by from transactions where id = :id")
                .param("id", id)
                .query(UUID.class)
                .single();
    }

    private BigDecimal balance(UUID account) {
        String accounts = body(get(ana, "/accounts"));
        List<String> amounts = JsonPath.read(accounts, "$[?(@.id=='" + account + "')].balance.amount");
        return new BigDecimal(amounts.getFirst());
    }

    private MvcTestResult post(UUID caller, String path, String body) {
        var request = mvc.post()
                .uri("/v1/workspaces/" + ws + path)
                .with(TestUsers.signedIn(caller, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(caller, null)));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    private MvcTestResult switchPlanningOff() {
        return mvc.put()
                .uri("/v1/workspaces/" + ws + "/modules/planning")
                .with(TestUsers.signedIn(ana, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}")
                .exchange();
    }

    private MvcTestResult get(UUID caller, String path) {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + path)
                .with(TestUsers.signedIn(caller, null))
                .exchange();
    }

    private static List<String> ids(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        return JsonPath.read(body(result), "$[*].id");
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}

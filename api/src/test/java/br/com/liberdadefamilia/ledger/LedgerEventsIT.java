package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Every change to a transaction or an account tells the rest of the app which accounts it touched. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import({TestcontainersConfiguration.class, LedgerEventsIT.Config.class})
class LedgerEventsIT {

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
    ApplicationEvents events;

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
        events.clear();
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void transactionMutationsPublish() {
        UUID banco = account("Banco");
        UUID id = TestIds.v7();

        assertThat(record(id)).hasStatus(201);
        assertThat(transactionChanges()).containsExactly(change(id, wallet, null));

        // A resend of the same transaction changes nothing, so it says nothing.
        assertThat(record(id)).hasStatusOk();
        assertThat(transactionChanges()).hasSize(1);

        assertThat(send("PATCH", "/transactions/" + id, expense(0, wallet, "20.00")))
                .hasStatusOk();
        assertThat(transactionChanges()).hasSize(2).last().isEqualTo(change(id, wallet, null));

        assertThat(send("PATCH", "/transactions/" + id, transfer(1, wallet, banco)))
                .hasStatusOk();
        assertThat(transactionChanges()).hasSize(3).last().isEqualTo(change(id, wallet, banco));

        // Moving it off the wallet touches the wallet too, so the old pair goes out as well.
        assertThat(send("PATCH", "/transactions/" + id, expense(2, banco, "20.00")))
                .hasStatusOk();
        assertThat(transactionChanges().subList(3, 5))
                .containsExactlyInAnyOrder(change(id, banco, null), change(id, wallet, banco));

        assertThat(send("DELETE", "/transactions/" + id, null)).hasStatus(204);
        assertThat(transactionChanges()).hasSize(6).last().isEqualTo(change(id, banco, null));

        assertThat(send("POST", "/transactions/" + id + "/restore", null)).hasStatusOk();
        assertThat(transactionChanges()).hasSize(7).last().isEqualTo(change(id, banco, null));

        UUID toConfirm = planned();
        assertThat(send("POST", "/transactions/" + toConfirm + "/confirm", "{\"version\":0}"))
                .hasStatusOk();
        assertThat(transactionChanges()).hasSize(8).last().isEqualTo(change(toConfirm, wallet, null));

        UUID toSkip = planned();
        assertThat(send("POST", "/transactions/" + toSkip + "/skip", "{\"version\":0}"))
                .hasStatus(204);
        assertThat(transactionChanges()).hasSize(9).last().isEqualTo(change(toSkip, wallet, null));
    }

    @Test
    void accountMutationsPublish() {
        UUID banco = account("Banco");

        assertThat(send("PATCH", "/accounts/" + banco, "{\"name\":\"Banco Novo\"}"))
                .hasStatusOk();
        assertThat(accountChanges()).containsExactly(new AccountChanged(ws, banco));

        assertThat(send("PATCH", "/accounts/" + banco, "{\"balance\":{\"amount\":\"50.00\",\"currency\":\"BRL\"}}"))
                .hasStatusOk();
        assertThat(accountChanges()).hasSize(2);

        assertThat(send("POST", "/accounts/" + banco + "/archive", null)).hasStatusOk();
        assertThat(accountChanges()).hasSize(3);

        assertThat(send("POST", "/accounts/" + banco + "/unarchive", null)).hasStatusOk();
        assertThat(accountChanges()).hasSize(4).allMatch(new AccountChanged(ws, banco)::equals);
    }

    @Test
    void createAccountPublishesAccountChanged() {
        var created = send("POST", "/accounts", """
                {"name":"Banco","type":"CHECKING","initialBalance":{"amount":"0.00","currency":"BRL"}}""");
        assertThat(created).hasStatus(201);
        UUID id = UUID.fromString(JsonPath.read(body(created), "$.id"));

        assertThat(accountChanges()).containsExactly(new AccountChanged(ws, id));
    }

    @Test
    void setOwnerPublishesFormerAndNewOwner() {
        UUID leo = users.create("Leo Teste", "leo@example.com");
        UUID leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bia");
        UUID leoWallet = jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, owner_membership_id)
                        values (:ws, 'Carteira do Leo', 'WALLET', 'BRL', :owner) returning id""")
                .param("ws", ws)
                .param("owner", leoMembership)
                .query(UUID.class)
                .single();
        events.clear();

        assertThat(send("PUT", "/accounts/" + leoWallet + "/owner", "{\"membershipId\":\"" + biaMembership + "\"}"))
                .hasStatusOk();

        assertThat(events.stream(AccountOwnerChanged.class).toList())
                .containsExactly(new AccountOwnerChanged(ws, leoWallet, leoMembership, biaMembership));
    }

    @Test
    void categoryLifecyclePublishes() {
        var created = send("POST", "/categories", """
                {"name":"Academia","kind":"EXPENSE","icon":"🏋️","color":"GREEN","parentId":null}""");
        assertThat(created).hasStatus(201);
        UUID id = UUID.fromString(JsonPath.read(body(created), "$.id"));

        assertThat(send("PATCH", "/categories/" + id, "{\"name\":\"Esporte\"}")).hasStatusOk();
        assertThat(send("POST", "/categories/" + id + "/archive", null)).hasStatusOk();
        assertThat(send("POST", "/categories/" + id + "/unarchive", null)).hasStatusOk();

        assertThat(events.stream(CategoryChanged.class).toList())
                .containsExactly(
                        new CategoryChanged(ws, id),
                        new CategoryChanged(ws, id),
                        new CategoryChanged(ws, id),
                        new CategoryChanged(ws, id));
    }

    @Test
    void tagRenamePublishes() {
        UUID tag = jdbc.sql("insert into tags (workspace_id, name) values (:ws, 'viagem') returning id")
                .param("ws", ws)
                .query(UUID.class)
                .single();

        assertThat(send("PATCH", "/tags/" + tag, "{\"name\":\"passeio\"}")).hasStatusOk();

        assertThat(events.stream(TagChanged.class).toList()).containsExactly(new TagChanged(ws, tag));
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private TransactionChanged change(UUID transactionId, UUID accountId, @Nullable UUID toAccountId) {
        return new TransactionChanged(ws, transactionId, accountId, toAccountId);
    }

    private List<TransactionChanged> transactionChanges() {
        return events.stream(TransactionChanged.class).toList();
    }

    private List<AccountChanged> accountChanges() {
        return events.stream(AccountChanged.class).toList();
    }

    private MvcTestResult record(UUID id) {
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/transactions")
                .with(TestUsers.signedIn(ana, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)))
                .header("Idempotency-Key", id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"id":"%s","kind":"EXPENSE","amount":{"amount":"12.34","currency":"BRL"},
                         "occurredOn":"2026-11-02","accountId":"%s","categoryId":"%s"}""".formatted(id, wallet, mercado))
                .exchange();
    }

    private String expense(long version, UUID account, String amount) {
        return """
                {"version":%d,"kind":"EXPENSE","amount":{"amount":"%s","currency":"BRL"},
                 "occurredOn":"2026-11-02","accountId":"%s","categoryId":"%s"}""".formatted(version, amount, account, mercado);
    }

    private String transfer(long version, UUID from, UUID to) {
        return """
                {"version":%d,"kind":"TRANSFER","amount":{"amount":"20.00","currency":"BRL"},
                 "occurredOn":"2026-11-02","accountId":"%s","toAccountId":"%s","categoryId":null}""".formatted(version, from, to);
    }

    private UUID planned() {
        return ledger.insertPlanned(
                ws, wallet, mercado, Money.brl("90.00"), LocalDate.of(2026, 11, 25), null, anaMembership);
    }

    private UUID account(String name) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency)
                        values (:ws, :name, 'CHECKING', 'BRL') returning id""")
                .param("ws", ws)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private MvcTestResult send(String method, String path, @Nullable String body) {
        var request = switch (method) {
            case "PATCH" -> mvc.patch();
            case "DELETE" -> mvc.delete();
            case "PUT" -> mvc.put();
            default -> mvc.post();
        };
        request.uri("/v1/workspaces/" + ws + path)
                .with(TestUsers.signedIn(ana, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }
}

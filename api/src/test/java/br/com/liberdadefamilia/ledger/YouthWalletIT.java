package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

/** A YOUTH who joins gets a wallet of their own; nobody else does. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class YouthWalletIT {

    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Test
    void youthGetsWallet() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", "leo@example.com");
        UUID ws = workspaces.createFor(ana);

        join(ana, ws, leo, "YOUTH", "Leo");

        UUID leoMembership = workspaces.membershipOf(ws, leo);
        Map<String, Object> wallet = jdbc.sql("""
                        select name, type, currency, initial_balance
                        from accounts
                        where workspace_id = :ws and owner_membership_id = :membership""")
                .param("ws", ws)
                .param("membership", leoMembership)
                .query()
                .singleRow();
        assertThat(wallet)
                .containsEntry("name", "Carteira de Leo")
                .containsEntry("type", "CASH")
                .containsEntry("currency", "BRL");
        assertThat((BigDecimal) wallet.get("initial_balance")).isZero();
        MvcTestResult accounts = mvc.get()
                .uri("/v1/workspaces/" + ws + "/accounts")
                .with(TestUsers.signedIn(ana, null))
                .exchange();
        assertThat(accounts).hasStatusOk();
        assertThat(accounts)
                .bodyJson()
                .extractingPath("$[?(@.name == 'Carteira de Leo')].balance.amount")
                .asArray()
                .containsExactly("0.00");
    }

    @Test
    void walletNameAvoidsClash() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", "leo@example.com");
        UUID leona = users.create("Leona Teste", "leona@example.com");
        UUID ws = workspaces.createFor(ana);
        jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance)
                        values (:ws, 'Carteira de Leo', 'CASH', 'BRL', 0)""").param("ws", ws).update();

        join(ana, ws, leo, "YOUTH", "Leo");
        join(ana, ws, leona, "YOUTH", "Leo");

        assertThat(walletNames(ws))
                .containsExactlyInAnyOrder("Carteira", "Carteira de Leo", "Carteira de Leo 2", "Carteira de Leo 3");
    }

    @Test
    void adultGetsNoWallet() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID vera = users.create("Vera Teste", "vera@example.com");
        UUID ws = workspaces.createFor(ana);

        join(ana, ws, bia, "ADULT", "Bibi");
        join(ana, ws, vera, "VIEWER", "Vera");

        assertThat(walletNames(ws)).containsExactly("Carteira");
    }

    private List<String> walletNames(UUID ws) {
        return jdbc.sql("select name from accounts where workspace_id = :ws")
                .param("ws", ws)
                .query(String.class)
                .list();
    }

    /** The owner invites through the API and the person takes the link, signed in. */
    private void join(UUID ownerId, UUID ws, UUID personId, String role, String nickname) {
        String consent = "YOUTH".equals(role) ? ",\"parentalConsent\":true" : "";
        RequestPostProcessor owner = TestUsers.signedIn(ownerId, null);
        MvcTestResult created = mvc.post()
                .uri("/v1/workspaces/" + ws + "/invitations")
                .with(owner)
                .with(CsrfSession.start(mvc, owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + role + "\",\"nickname\":\"" + nickname + "\"" + consent + "}")
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
                    request.setRemoteAddr("10.3." + (n / 250) + "." + (n % 250 + 1));
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

package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** An account marked as the emergency reserve stays marked until someone unmarks it. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountReserveFlagIT {

    @Autowired
    MockMvcTester mvc;

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
    void patchSetsAndKeeps() {
        assertThat(list()).bodyJson().extractingPath("$[0].isReserve").isEqualTo(false);

        var marked = patch("{\"isReserve\":true}");

        assertThat(marked).hasStatusOk();
        assertThat(marked).bodyJson().extractingPath("$.isReserve").isEqualTo(true);
        assertThat(list()).bodyJson().extractingPath("$[0].isReserve").isEqualTo(true);

        var renamed = patch("{\"name\":\"Cofre\"}");

        assertThat(renamed).hasStatusOk();
        assertThat(renamed).bodyJson().extractingPath("$.isReserve").isEqualTo(true);
        assertThat(list()).bodyJson().extractingPath("$[0].isReserve").isEqualTo(true);

        assertThat(patch("{\"isReserve\":false}"))
                .bodyJson()
                .extractingPath("$.isReserve")
                .isEqualTo(false);
    }

    private MvcTestResult list() {
        return mvc.get()
                .uri("/v1/workspaces/" + workspaceId + "/accounts")
                .with(TestUsers.signedIn(anaId, null))
                .exchange();
    }

    private MvcTestResult patch(String body) {
        return mvc.patch()
                .uri("/v1/workspaces/" + workspaceId + "/accounts/" + walletId)
                .with(TestUsers.signedIn(anaId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(anaId, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}

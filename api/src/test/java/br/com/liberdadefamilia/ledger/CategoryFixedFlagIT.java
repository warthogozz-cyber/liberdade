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

/** Only an expense category can be fixed, and it stays fixed until someone says otherwise. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CategoryFixedFlagIT {

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

    @BeforeEach
    void family() {
        anaId = users.create("Ana Teste", "ana@example.com");
        workspaceId = workspaces.createFor(anaId);
    }

    @Test
    void onlyExpenseCanBeFixed() {
        UUID salario = ledger.categoryOf(workspaceId, "INCOME", "Salário");

        var result = patch(salario, "{\"isFixed\":true}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("category.fixed-not-expense");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Só categorias de gasto podem ser fixas.");
        // Clearing the flag of an income category is harmless.
        assertThat(patch(salario, "{\"isFixed\":false}"))
                .bodyJson()
                .extractingPath("$.isFixed")
                .isEqualTo(false);
    }

    @Test
    void patchSetsAndKeeps() {
        UUID moradia = ledger.categoryOf(workspaceId, "EXPENSE", "Moradia");

        var fixed = patch(moradia, "{\"isFixed\":true}");

        assertThat(fixed).hasStatusOk();
        assertThat(fixed).bodyJson().extractingPath("$.isFixed").isEqualTo(true);

        var renamed = patch(moradia, "{\"name\":\"Casa\"}");

        assertThat(renamed).hasStatusOk();
        assertThat(renamed).bodyJson().extractingPath("$.name").isEqualTo("Casa");
        assertThat(renamed).bodyJson().extractingPath("$.isFixed").isEqualTo(true);
        assertThat(mvc.get()
                        .uri("/v1/workspaces/" + workspaceId + "/categories?kind=EXPENSE")
                        .with(TestUsers.signedIn(anaId, null))
                        .exchange())
                .bodyJson()
                .extractingPath("$[?(@.id == '" + moradia + "')].isFixed")
                .isEqualTo(java.util.List.of(true));
    }

    private MvcTestResult patch(UUID categoryId, String body) {
        return mvc.patch()
                .uri("/v1/workspaces/" + workspaceId + "/categories/" + categoryId)
                .with(TestUsers.signedIn(anaId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(anaId, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}

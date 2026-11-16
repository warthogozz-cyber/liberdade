package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MultipleFamiliesIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Test
    void personInTwoFamiliesSeesBothAndFamilyInvitedFlags() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID anaFamily = workspaces.createFor(ana);
        UUID biaFamily = workspaces.createFor(bia);
        workspaces.addMember(biaFamily, ana, Role.ADULT);

        var result = mvc.get().uri("/v1/me").with(TestUsers.signedIn(ana, null)).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.workspaces").asArray().hasSize(2);
        assertThat(result).bodyJson().extractingPath("$.workspaces[0].id").isEqualTo(anaFamily.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.workspaces[0].familyInvited")
                .isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.workspaces[1].id").isEqualTo(biaFamily.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.workspaces[1].familyInvited")
                .isEqualTo(true);
    }

    @Test
    void memberOfOneCanCreateOwnFamily() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID anaFamily = workspaces.createFor(ana);
        workspaces.addMember(anaFamily, bia, Role.ADULT);

        var owner = create(ana);
        assertThat(owner).hasStatus(409);
        assertThat(owner).bodyJson().extractingPath("$.code").isEqualTo("workspace.already-exists");
        assertThat(create(bia)).hasStatus(201);

        var me = mvc.get().uri("/v1/me").with(TestUsers.signedIn(bia, null)).exchange();
        assertThat(me).hasStatusOk();
        assertThat(me).bodyJson().extractingPath("$.workspaces").asArray().hasSize(2);
        assertThat(me).bodyJson().extractingPath("$.workspaces[0].id").isEqualTo(anaFamily.toString());
        assertThat(me).bodyJson().extractingPath("$.workspaces[0].role").isEqualTo("ADULT");
        assertThat(me).bodyJson().extractingPath("$.workspaces[1].role").isEqualTo("OWNER");
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult create(UUID userId) {
        return mvc.post()
                .uri("/v1/workspaces")
                .with(TestUsers.signedIn(userId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(userId, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Família Teste\"}")
                .exchange();
    }
}

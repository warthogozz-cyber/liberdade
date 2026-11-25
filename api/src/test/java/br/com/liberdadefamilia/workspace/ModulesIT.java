package br.com.liberdadefamilia.workspace;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The family's optional modules. The probe routes live in this class and are imported into this context only, so the
 * route-completeness checks of {@code RoleMatrixIT} and {@code WorkspaceGuardCoverageIT}, which run in their own
 * contexts, never see them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ModulesIT.Probe.class, ModulesIT.Listener.class})
class ModulesIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Listener {

        @Bean
        Toggles toggles() {
            return new Toggles();
        }
    }

    static class Toggles {

        private final List<ModuleToggled> seen = new CopyOnWriteArrayList<>();

        @EventListener
        void on(ModuleToggled event) {
            seen.add(event);
        }

        List<ModuleToggled> events() {
            return seen;
        }
    }

    /** Family routes of the planning module, declared as the real ones will be. */
    @RestController
    static class Probe {

        @GetMapping("/v1/workspaces/{ws}/module-probe/planning")
        @WorkspaceScoped(allow = {OWNER, ADULT, VIEWER})
        @RequiresModule(ModuleKey.PLANNING)
        String planning() {
            return "planning";
        }

        /** A role the route does not allow must hear "forbidden", never "module disabled". */
        @GetMapping("/v1/workspaces/{ws}/module-probe/owner-only")
        @WorkspaceScoped(allow = OWNER)
        @RequiresModule(ModuleKey.PLANNING)
        String ownerOnly() {
            return "owner";
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
    Toggles toggles;

    @Autowired
    Modules modules;

    @Test
    void newFamilyHasPlanningOn() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);

        MvcTestResult result = get(ana, "/v1/workspaces/" + ws + "/modules");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(5);
        assertThat(result)
                .bodyJson()
                .extractingPath("$[*].key")
                .asArray()
                .containsExactly("planning", "indicators", "gamification", "ventures", "markets");
        assertThat(result)
                .bodyJson()
                .extractingPath("$[*].enabled")
                .asArray()
                .containsExactly(true, false, false, false, false);
        assertThat(modules.isEnabled(ws, ModuleKey.PLANNING)).isTrue();
        assertThat(modules.isEnabled(ws, ModuleKey.INDICATORS)).isFalse();
        assertThat(modules.isEnabled(ws, ModuleKey.GAMIFICATION)).isFalse();
        assertThat(modules.isEnabled(ws, ModuleKey.VENTURES)).isFalse();
    }

    @Test
    void adultTurnsOn() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID vera = users.create("Vera Teste", "vera@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, ADULT);
        workspaces.addMember(ws, vera, VIEWER);

        MvcTestResult put = put(bia, ws, "indicators", "{\"enabled\":true}");

        assertThat(put).hasStatusOk();
        assertThat(put).bodyJson().extractingPath("$.key").isEqualTo("indicators");
        assertThat(put).bodyJson().extractingPath("$.enabled").isEqualTo(true);
        for (UUID caller : List.of(ana, bia, vera)) {
            MvcTestResult list = get(caller, "/v1/workspaces/" + ws + "/modules");
            assertThat(list).hasStatusOk();
            assertThat(list)
                    .bodyJson()
                    .extractingPath("$[*].enabled")
                    .asArray()
                    .containsExactly(true, true, false, false, false);
        }
        assertThat(modules.isEnabled(ws, ModuleKey.INDICATORS)).isTrue();
    }

    @Test
    void turningOffKeepsRow() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        assertThat(put(ana, ws, "ventures", "{\"enabled\":true}")).hasStatusOk();

        MvcTestResult off = put(ana, ws, "ventures", "{\"enabled\":false}");

        assertThat(off).hasStatusOk();
        assertThat(off).bodyJson().extractingPath("$.enabled").isEqualTo(false);
        assertThat(jdbc.sql("""
                                select enabled from workspace_modules
                                where workspace_id = :ws and module_key = 'ventures'""").param("ws", ws).query(Boolean.class).list()).containsExactly(false);
        assertThat(toggles.events().stream()
                        .filter(event -> event.workspaceId().equals(ws))
                        .map(ModuleToggled::enabled))
                .containsExactly(true, false);
    }

    @Test
    void sameValueNoEvent() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);

        assertThat(put(ana, ws, "indicators", "{\"enabled\":true}")).hasStatusOk();
        assertThat(put(ana, ws, "indicators", "{\"enabled\":true}")).hasStatusOk();
        // Off is the default, so switching off a module that has no row changes nothing either.
        assertThat(put(ana, ws, "gamification", "{\"enabled\":false}")).hasStatusOk();

        assertThat(toggles.events().stream().filter(event -> event.workspaceId().equals(ws)))
                .containsExactly(new ModuleToggled(ws, ModuleKey.INDICATORS, true, ana));
    }

    @Test
    void unknownKeyIs404() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);

        MvcTestResult result = put(ana, ws, "budgets", "{\"enabled\":true}");

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("module.not-found");
    }

    @Test
    void missingEnabledIs422() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);

        MvcTestResult result = put(ana, ws, "planning", "{}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
    }

    @Test
    void disabledModuleRouteIs403() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID vera = users.create("Vera Teste", "vera@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, vera, VIEWER);
        String route = "/v1/workspaces/" + ws + "/module-probe/planning";

        // Planning is on in a new family: the route answers 403 once it is switched off.
        assertThat(put(ana, ws, "planning", "{\"enabled\":false}")).hasStatusOk();
        MvcTestResult off = get(vera, route);
        assertThat(off).hasStatus(403);
        assertThat(off).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
        assertThat(off).bodyJson().extractingPath("$.detail").isEqualTo("Este módulo está desligado na sua família.");

        assertThat(put(ana, ws, "planning", "{\"enabled\":true}")).hasStatusOk();
        assertThat(get(vera, route)).hasStatusOk();
    }

    @Test
    void roleIsCheckedBeforeModule() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID vera = users.create("Vera Teste", "vera@example.com");
        UUID ws = workspaces.createFor(ana);
        workspaces.addMember(ws, vera, VIEWER);

        MvcTestResult result = get(vera, "/v1/workspaces/" + ws + "/module-probe/owner-only");

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
    }

    private MvcTestResult get(UUID caller, String uri) {
        return mvc.get().uri(uri).with(TestUsers.signedIn(caller, null)).exchange();
    }

    private MvcTestResult put(UUID caller, UUID ws, String key, String body) {
        return mvc.put()
                .uri("/v1/workspaces/" + ws + "/modules/" + key)
                .with(TestUsers.signedIn(caller, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(caller, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}

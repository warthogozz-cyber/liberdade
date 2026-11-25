package br.com.liberdadefamilia.ventures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Creating, reading, editing and deleting a project, the input limits and the Grátis plan limit. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, VenturesIT.Config.class})
class VenturesIT {

    private static final String LIMIT_DETAIL =
            "No plano Grátis, a família tem 1 projeto ativo. Encerre um, ou veja o" + " plano Família em Mais > Plano.";

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
    TestClock clock;

    @MockitoSpyBean
    Plans plans;

    private UUID ana;
    private UUID ws;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        modules(true);
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void createBlankHasGenericSteps() {
        var result = create("{\"name\":\"Meu projeto\",\"type\":\"OTHER\"}");

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PLANNING");
        assertThat(result).bodyJson().extractingPath("$.steps.length()").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.initialItems.length()").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.products.length()").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.currency").isEqualTo("BRL");
        assertThat(result).bodyJson().extractingPath("$.templateKey").isNull();
    }

    @Test
    void createFromFoodSweets() {
        var result = create("{\"templateKey\":\"food-sweets\",\"name\":\"Doces da Ana\"}");

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("FOOD");
        assertThat(result).bodyJson().extractingPath("$.templateKey").isEqualTo("food-sweets");
        assertThat(result).bodyJson().extractingPath("$.steps.length()").isEqualTo(6);
        assertThat(result).bodyJson().extractingPath("$.initialItems.length()").isEqualTo(3);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.initialItems[0].isExample")
                .isEqualTo(true);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.initialItems[0].amount.amount")
                .isEqualTo("350.00");
        assertThat(result).bodyJson().extractingPath("$.fixedItems.length()").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.products[0].isExample").isEqualTo(true);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[0].unitPrice.amount")
                .isEqualTo("4.50");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.figures.monthlyProfit.amount")
                .isEqualTo("540.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.figures.initialInvestment.amount")
                .isEqualTo("800.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.figures.breakEven.state")
                .isEqualTo("OK");
    }

    @Test
    void unknownTemplateIs422() {
        var result = create("{\"templateKey\":\"x\",\"name\":\"Doces\"}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("venture.template-unknown");
    }

    @Test
    void nonBrlFamilyGetsNoValues() {
        jdbc.sql("update workspaces set base_currency = 'USD' where id = :ws")
                .param("ws", ws)
                .update();

        var result = create("{\"templateKey\":\"food-sweets\",\"name\":\"Sweets\"}");

        assertThat(result).hasStatus(201);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("FOOD");
        assertThat(result).bodyJson().extractingPath("$.currency").isEqualTo("USD");
        assertThat(result).bodyJson().extractingPath("$.steps.length()").isEqualTo(6);
        assertThat(result).bodyJson().extractingPath("$.initialItems.length()").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.fixedItems.length()").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.products.length()").isEqualTo(0);
    }

    @Test
    void editingAnExampleClearsTheBadge() {
        var created = create("{\"templateKey\":\"food-sweets\",\"name\":\"Doces\"}");
        String item = read(created, "$.initialItems[0].id");
        String item2 = read(created, "$.initialItems[1].id");
        String item3 = read(created, "$.initialItems[2].id");
        String product = read(created, "$.products[0].id");

        var updated = update(id(created), """
                {"version":0,"name":"Doces","type":"FOOD","startedOn":null,
                 "initialItems":[{"id":"%s","name":"Panelas e utensílios","amount":"350.00"},
                                 {"id":"%s","name":"Batedeira","amount":"300.00"},
                                 {"id":"%s","name":"Embalagens e forminhas iniciais","amount":"150.00"}],
                 "fixedItems":[],
                 "products":[{"id":"%s","name":"Brigadeiro gourmet","unitVariableCost":"1.80","unitPrice":"5.00",
                              "plannedUnitsMonth":300}]}""".formatted(item, item2, item3, product));

        assertThat(updated).hasStatus(200);
        assertThat(updated)
                .bodyJson()
                .extractingPath("$.initialItems[0].isExample")
                .isEqualTo(true);
        assertThat(updated).bodyJson().extractingPath("$.products[0].isExample").isEqualTo(false);
        assertThat(updated)
                .bodyJson()
                .extractingPath("$.products[0].unitPrice.amount")
                .isEqualTo("5.00");
        assertThat(updated).bodyJson().extractingPath("$.version").isEqualTo(1);
    }

    @Test
    void putReplacesItemsInOrder() {
        var created = create("{\"templateKey\":\"food-sweets\",\"name\":\"Doces\"}");
        String keep = read(created, "$.initialItems[2].id");

        var updated = update(id(created), """
                {"version":0,"name":"Doces","type":"FOOD","startedOn":null,
                 "initialItems":[{"name":"Novo","amount":"10.00"},{"id":"%s","name":"Embalagens","amount":"150.00"}],
                 "fixedItems":[],"products":[]}""".formatted(keep));

        assertThat(updated).hasStatus(200);
        assertThat(updated).bodyJson().extractingPath("$.initialItems.length()").isEqualTo(2);
        assertThat(updated).bodyJson().extractingPath("$.initialItems[0].name").isEqualTo("Novo");
        assertThat(updated).bodyJson().extractingPath("$.initialItems[1].id").isEqualTo(keep);
        assertThat(updated).bodyJson().extractingPath("$.fixedItems.length()").isEqualTo(0);
        assertThat(updated).bodyJson().extractingPath("$.products.length()").isEqualTo(0);
    }

    @Test
    void staleVersionIs409() {
        UUID venture = id(create("{\"name\":\"Doces\",\"type\":\"OTHER\"}"));
        assertThat(update(venture, blankUpdate(0, "Um"))).hasStatus(200);

        var stale = update(venture, blankUpdate(0, "Dois"));

        assertThat(stale).hasStatus(409);
        assertThat(stale).bodyJson().extractingPath("$.code").isEqualTo("venture.version-conflict");
    }

    @Test
    void stalePutAfterActivationIs409NotStartedOnInvalid() {
        UUID venture = id(create("{\"name\":\"Doces\",\"type\":\"OTHER\"}"));
        assertThat(status(venture, "{\"version\":0,\"status\":\"ACTIVE\",\"on\":null}"))
                .hasStatus(200);

        var stale = update(venture, blankUpdate(0, "Dois"));

        assertThat(stale).hasStatus(409);
        assertThat(stale).bodyJson().extractingPath("$.code").isEqualTo("venture.version-conflict");
    }

    @Test
    void limitsAre422() {
        UUID venture = id(create("{\"name\":\"Doces\",\"type\":\"OTHER\"}"));
        String twentyOne =
                String.join(",", java.util.Collections.nCopies(21, "{\"name\":\"Item\",\"amount\":\"10.00\"}"));
        String eleven = String.join(
                ",",
                java.util.Collections.nCopies(
                        11,
                        "{\"name\":\"P\",\"unitVariableCost\":\"1.00\",\"unitPrice\":\"2.00\",\"plannedUnitsMonth\":1}"));

        assertInvalid(update(venture, withLists(0, "Doces", twentyOne, "")));
        assertInvalid(update(venture, withLists(0, "Doces", "", eleven)));
        assertInvalid(update(venture, blankUpdate(0, "x".repeat(41))));
        assertInvalid(update(venture, withLists(0, "Doces", "{\"name\":\"I\",\"amount\":\"1000000000.00\"}", "")));
        assertInvalid(update(venture, withLists(0, "Doces", "{\"name\":\"I\",\"amount\":\"1.234\"}", "")));
        assertInvalid(update(venture, withLists(0, "Doces", "{\"name\":\"I\",\"amount\":\"0.00\"}", "")));
        assertInvalid(update(venture, withLists(0, "Doces", "{\"name\":\"\",\"amount\":\"1.00\"}", "")));
        assertInvalid(create("{\"name\":\"" + "y".repeat(41) + "\",\"type\":\"OTHER\"}"));
    }

    @Test
    void startedOnRules() {
        UUID venture = id(create("{\"name\":\"Doces\",\"type\":\"OTHER\"}"));

        var planningWithStart = update(venture, startedOn(0, "\"2026-11-10\""));
        assertThat(planningWithStart).hasStatus(422);
        assertThat(planningWithStart).bodyJson().extractingPath("$.code").isEqualTo("venture.started-on-invalid");

        var activated = status(venture, "{\"version\":0,\"status\":\"ACTIVE\",\"on\":\"2026-11-10\"}");
        assertThat(activated).hasStatus(200);

        var withoutStart = update(venture, startedOn(1, "null"));
        assertThat(withoutStart).hasStatus(422);
        assertThat(withoutStart).bodyJson().extractingPath("$.code").isEqualTo("venture.started-on-invalid");

        var future = update(venture, startedOn(1, "\"2026-11-21\""));
        assertThat(future).hasStatus(422);
        assertThat(future).bodyJson().extractingPath("$.code").isEqualTo("venture.started-on-invalid");

        assertThat(update(venture, startedOn(1, "\"2026-11-05\""))).hasStatus(200);
    }

    @Test
    void freeFamilySecondVentureIsLimited() {
        assertThat(create("{\"name\":\"Primeiro\",\"type\":\"OTHER\"}")).hasStatus(201);

        var second = create("{\"templateKey\":\"food-sweets\",\"name\":\"Segundo\"}");

        assertThat(second).hasStatus(403);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo("venture.limit-reached");
        assertThat(second).bodyJson().extractingPath("$.detail").isEqualTo(LIMIT_DETAIL);
        var list = send(HttpMethod.GET, "/ventures", null);
        assertThat(list).bodyJson().extractingPath("$.limitReached").isEqualTo(true);
        assertThat(list).bodyJson().extractingPath("$.ventures.length()").isEqualTo(1);
    }

    @Test
    void closedDoesNotCount() {
        UUID first = id(create("{\"name\":\"Primeiro\",\"type\":\"OTHER\"}"));
        assertThat(status(first, "{\"version\":0,\"status\":\"CLOSED\",\"on\":null}"))
                .hasStatus(200);

        assertThat(create("{\"name\":\"Segundo\",\"type\":\"OTHER\"}")).hasStatus(201);
        assertThat(send(HttpMethod.GET, "/ventures", null))
                .bodyJson()
                .extractingPath("$.ventures.length()")
                .isEqualTo(2);
    }

    @Test
    void familyPlanAndBillingOffHaveNoLimit() {
        jdbc.sql("""
                        insert into workspace_plans (workspace_id, plan_key, founder, starts_at, ends_at)
                        values (:ws, 'FAMILY', false, :starts, :ends)""")
                .param("ws", ws)
                .param("starts", Timestamp.from(Instant.parse("2026-11-01T00:00:00Z")))
                .param("ends", Timestamp.from(Instant.parse("2027-11-20T12:00:00Z")))
                .update();
        for (int i = 0; i < 3; i++) {
            assertThat(create("{\"name\":\"Projeto\",\"type\":\"OTHER\"}")).hasStatus(201);
        }
        jdbc.sql("delete from workspace_plans where workspace_id = :ws")
                .param("ws", ws)
                .update();
        jdbc.sql("delete from ventures where workspace_id = :ws")
                .param("ws", ws)
                .update();

        doReturn(false).when(plans).legalSetEnabled();
        for (int i = 0; i < 3; i++) {
            assertThat(create("{\"name\":\"Projeto\",\"type\":\"OTHER\"}")).hasStatus(201);
        }
        assertThat(send(HttpMethod.GET, "/ventures", null))
                .bodyJson()
                .extractingPath("$.limitReached")
                .isEqualTo(false);
    }

    @Test
    void viewerNeverSeesLimitReached() {
        assertThat(create("{\"name\":\"Primeiro\",\"type\":\"OTHER\"}")).hasStatus(201);
        UUID vera = users.create("Vera Teste", "vera@example.com");
        workspaces.addMember(ws, vera, Role.VIEWER, "Vera");

        var list = mvc.get()
                .uri("/v1/workspaces/" + ws + "/ventures")
                .with(TestUsers.signedIn(vera, null))
                .exchange();

        assertThat(list).hasStatus(200);
        assertThat(list).bodyJson().extractingPath("$.limitReached").isEqualTo(false);
        assertThat(list).bodyJson().extractingPath("$.ventures.length()").isEqualTo(1);
    }

    @Test
    void otherFamilyIs404() {
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID other = workspaces.createFor(bia);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'ventures', true)")
                .param("ws", other)
                .update();
        UUID foreign = jdbc.sql("""
                        insert into ventures (workspace_id, name, type, currency, created_by)
                        values (:ws, 'Dela', 'OTHER', 'BRL', :m)
                        returning id""")
                .param("ws", other)
                .param("m", workspaces.membershipOf(other, bia))
                .query(UUID.class)
                .single();

        for (var result : List.of(
                send(HttpMethod.GET, "/ventures/" + foreign, null),
                send(HttpMethod.PUT, "/ventures/" + foreign, blankUpdate(0, "X")),
                send(HttpMethod.POST, "/ventures/" + foreign + "/status", "{\"version\":0,\"status\":\"ACTIVE\"}"),
                send(HttpMethod.DELETE, "/ventures/" + foreign, null))) {
            assertThat(result).hasStatus(404);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("venture.not-found");
        }
        assertThat(jdbc.sql("select count(*) from ventures where id = :id")
                        .param("id", foreign)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void deleteRemovesEverything() {
        UUID venture = id(create("{\"templateKey\":\"food-sweets\",\"name\":\"Doces\"}"));

        assertThat(send(HttpMethod.DELETE, "/ventures/" + venture, null)).hasStatus(204);

        for (String table : new String[] {"ventures", "venture_cost_items", "venture_products", "venture_steps"}) {
            String column = table.equals("ventures") ? "id" : "venture_id";
            assertThat(jdbc.sql("select count(*) from " + table + " where " + column + " = :id")
                            .param("id", venture)
                            .query(Integer.class)
                            .single())
                    .as(table)
                    .isZero();
        }
        assertThat(send(HttpMethod.DELETE, "/ventures/" + venture, null)).hasStatus(404);
    }

    @Test
    void moduleOffIs403() {
        modules(false);

        var list = send(HttpMethod.GET, "/ventures", null);
        var created = create("{\"name\":\"Doces\",\"type\":\"OTHER\"}");

        assertThat(list).hasStatus(403);
        assertThat(list).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
        assertThat(created).hasStatus(403);
        assertThat(created).bodyJson().extractingPath("$.code").isEqualTo("module.disabled");
    }

    @Test
    void templatesListTheTracks() {
        var result = send(HttpMethod.GET, "/ventures/templates", null);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.tracks").asList().isNotEmpty();
        List<String> profit = JsonPath.read(
                body(result), "$.tracks[*].templates[?(@.key=='food-sweets')].figures.monthlyProfit.amount");
        assertThat(profit).containsExactly("540.00");
    }

    @Test
    void logsCarryNoNames(CapturedOutput output) {
        UUID venture = id(create("{\"name\":\"Segredo Doce\",\"type\":\"OTHER\"}"));
        assertThat(update(
                        venture, withLists(0, "Segredo Doce", "{\"name\":\"Item Secreto\",\"amount\":\"123.45\"}", "")))
                .hasStatus(200);

        assertThat(output.getAll()).doesNotContain("Segredo Doce", "Item Secreto", "123.45");
    }

    // --- helpers ---

    private void modules(boolean on) {
        jdbc.sql("delete from workspace_modules where workspace_id = :ws and module_key = 'ventures'")
                .param("ws", ws)
                .update();
        if (on) {
            jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'ventures', true)")
                    .param("ws", ws)
                    .update();
        }
    }

    private static String blankUpdate(long version, String name) {
        return withLists(version, name, "", "");
    }

    private static String withLists(long version, String name, String initial, String products) {
        return """
                {"version":%d,"name":"%s","type":"OTHER","startedOn":null,
                 "initialItems":[%s],"fixedItems":[],"products":[%s]}""".formatted(version, name, initial, products);
    }

    private static String startedOn(long version, String startedOn) {
        return """
                {"version":%d,"name":"Doces","type":"OTHER","startedOn":%s,
                 "initialItems":[],"fixedItems":[],"products":[]}""".formatted(version, startedOn);
    }

    private static void assertInvalid(MvcTestResult result) {
        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("venture.invalid");
    }

    private MvcTestResult create(String body) {
        return send(HttpMethod.POST, "/ventures", body);
    }

    private MvcTestResult update(UUID venture, String body) {
        return send(HttpMethod.PUT, "/ventures/" + venture, body);
    }

    private MvcTestResult status(UUID venture, String body) {
        return send(HttpMethod.POST, "/ventures/" + venture + "/status", body);
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    private static UUID id(MvcTestResult result) {
        assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(201);
        return UUID.fromString(read(result, "$.id"));
    }

    private MvcTestResult send(HttpMethod method, String path, @Nullable String body) {
        var request = mvc.method(method).uri("/v1/workspaces/" + ws + path).with(TestUsers.signedIn(ana, null));
        if (method != HttpMethod.GET) {
            request.with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }
}

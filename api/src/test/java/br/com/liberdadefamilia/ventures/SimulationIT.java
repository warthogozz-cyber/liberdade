package br.com.liberdadefamilia.ventures;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.Collections;
import java.util.UUID;
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

/** The two previews: the project's figures from the numbers on the form, and the investment simulator. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SimulationIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    private UUID ana;
    private UUID ws;

    @BeforeEach
    void family() {
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'ventures', true)")
                .param("ws", ws)
                .update();
    }

    @Test
    void simulationMatchesThePrd() {
        var result = simulate("""
                {"initialItems":[{"amount":"800.00"}],"fixedItems":[{"amount":"270.00"}],
                 "products":[{"unitVariableCost":"1.80","unitPrice":"4.50","plannedUnitsMonth":300}]}""");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.monthlyProfit.amount").isEqualTo("540.00");
        assertThat(result).bodyJson().extractingPath("$.monthlyProfit.currency").isEqualTo("BRL");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.initialInvestment.amount")
                .isEqualTo("800.00");
        assertThat(result).bodyJson().extractingPath("$.fixedMonthly.amount").isEqualTo("270.00");
        assertThat(result).bodyJson().extractingPath("$.plannedRevenue.amount").isEqualTo("1350.00");
        assertThat(result).bodyJson().extractingPath("$.plannedCost.amount").isEqualTo("810.00");
        assertThat(result).bodyJson().extractingPath("$.totalMargin.amount").isEqualTo("810.00");
        assertThat(result).bodyJson().extractingPath("$.roi12mPercent").isEqualTo("710.0");
        assertThat(result).bodyJson().extractingPath("$.roiState").isEqualTo("OK");
        assertThat(result).bodyJson().extractingPath("$.paybackMonths").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.paybackState").isEqualTo("MONTHS");
        assertThat(result).bodyJson().extractingPath("$.breakEven.state").isEqualTo("OK");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.breakEven.revenue.amount")
                .isEqualTo("450.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.breakEven.weightedMarginPercent")
                .isEqualTo("60.0");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[0].margin.amount")
                .isEqualTo("2.70");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[0].marginPercent")
                .isEqualTo("60.0");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[0].priceCoversCost")
                .isEqualTo(true);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.products[0].breakEvenUnits")
                .isEqualTo(100);
    }

    @Test
    void simulationAcceptsAnEmptyForm() {
        var result = simulate("{\"initialItems\":[],\"fixedItems\":[],\"products\":[]}");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.breakEven.state").isEqualTo("NO_PRODUCTS");
        assertThat(result).bodyJson().extractingPath("$.monthlyProfit.amount").isEqualTo("0.00");
    }

    @Test
    void simulationValidatesLikePut() {
        assertInvalid(simulate("{\"initialItems\":[{\"amount\":\"-1.00\"}],\"fixedItems\":[],\"products\":[]}"));

        String product = "{\"unitVariableCost\":\"1.00\",\"unitPrice\":\"2.00\",\"plannedUnitsMonth\":1}";
        String eleven = String.join(",", Collections.nCopies(11, product));
        assertInvalid(simulate("{\"initialItems\":[],\"fixedItems\":[],\"products\":[" + eleven + "]}"));

        String cost = "{\"amount\":\"1.00\"}";
        String twentyOne = String.join(",", Collections.nCopies(21, cost));
        assertInvalid(simulate("{\"initialItems\":[" + twentyOne + "],\"fixedItems\":[],\"products\":[]}"));
        assertInvalid(simulate("{\"initialItems\":[],\"fixedItems\":[],\"products\":[{\"unitVariableCost\":\"1.00\","
                + "\"unitPrice\":\"2.00\",\"plannedUnitsMonth\":1000001}]}"));
    }

    @Test
    void simulationWritesNothing() {
        long before = rows();

        assertThat(simulate("{\"initialItems\":[{\"amount\":\"10.00\"}],\"fixedItems\":[],\"products\":[]}"))
                .hasStatus(200);
        assertThat(invest(body("1000", "0", 2, "a", "5"))).hasStatus(200);

        assertThat(rows()).isEqualTo(before);
    }

    @Test
    void investmentThreeScenarios() {
        var result = invest("""
                {"initialAmount":"5000","monthlyContribution":"200","years":5,
                 "scenarios":[{"name":"low","annualPercent":"6"},{"name":"mid","annualPercent":"10.5"},
                              {"name":"none","annualPercent":"0"}]}""");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.scenarios[0].name").isEqualTo("low");
        assertThat(result).bodyJson().extractingPath("$.scenarios[1].name").isEqualTo("mid");
        assertThat(result).bodyJson().extractingPath("$.scenarios[2].name").isEqualTo("none");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.scenarios[0].finalBalance.amount")
                .isEqualTo("20588.29");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.scenarios[1].finalBalance.amount")
                .isEqualTo("23735.37");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.scenarios[2].finalBalance.amount")
                .isEqualTo("17000.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.scenarios[2].finalBalance.currency")
                .isEqualTo("BRL");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.scenarios[2].totalContributed.amount")
                .isEqualTo("17000.00");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.scenarios[2].earnings.amount")
                .isEqualTo("0.00");
    }

    @Test
    void investmentLimits() {
        assertInvestmentInvalid(body("1000", "0", 0, "a", "5"));
        assertInvestmentInvalid(body("1000", "0", 51, "a", "5"));
        assertInvestmentInvalid(body("1000", "0", 5, "a", "100.01"));
        assertInvestmentInvalid(body("1000", "0", 5, "a", "-1"));
        assertInvestmentInvalid(body("1000", "0", 5, "a", "5.123"));
        assertInvestmentInvalid(body("1000", "0", 5, "", "5"));
        assertInvestmentInvalid(body("1000", "0", 5, "x".repeat(31), "5"));
        assertInvestmentInvalid(body("-1", "0", 5, "a", "5"));
        assertInvestmentInvalid(body("1000", "1000000000", 5, "a", "5"));
        assertInvestmentInvalid(
                "{\"initialAmount\":\"1000\",\"monthlyContribution\":\"0\",\"years\":5,\"scenarios\":[]}");
        String four = String.join(",", Collections.nCopies(4, "{\"name\":\"a\",\"annualPercent\":\"5\"}"));
        assertInvestmentInvalid(
                "{\"initialAmount\":\"1000\",\"monthlyContribution\":\"0\",\"years\":5,\"scenarios\":[" + four + "]}");
        assertThat(invest(body("1000", "0", 5, "a", "100"))).hasStatus(200);
        assertThat(invest(body("1000", "0", 50, "x".repeat(30), "0"))).hasStatus(200);
    }

    @Test
    void investmentRefusesAResultTooBigToAnswer() {
        assertInvestmentInvalid(body("999999999.99", "999999999.99", 50, "a", "100"));
    }

    private static String body(String initial, String monthly, int years, String name, String percent) {
        return ("{\"initialAmount\":\"%s\",\"monthlyContribution\":\"%s\",\"years\":%d,"
                        + "\"scenarios\":[{\"name\":\"%s\",\"annualPercent\":\"%s\"}]}")
                .formatted(initial, monthly, years, name, percent);
    }

    private long rows() {
        long total = 0;
        for (String table : new String[] {"ventures", "venture_cost_items", "venture_products", "venture_steps"}) {
            total += jdbc.sql("select count(*) from " + table).query(Long.class).single();
        }
        return total;
    }

    private static void assertInvalid(MvcTestResult result) {
        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("venture.invalid");
    }

    private void assertInvestmentInvalid(String body) {
        var result = invest(body);
        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("investment-simulation.invalid");
    }

    private MvcTestResult simulate(String body) {
        return post("/ventures/simulation", body);
    }

    private MvcTestResult invest(String body) {
        return post("/ventures/investment-simulation", body);
    }

    private MvcTestResult post(String path, String body) {
        return mvc.post()
                .uri("/v1/workspaces/" + ws + path)
                .with(TestUsers.signedIn(ana, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}

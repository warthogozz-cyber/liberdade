package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The plan guard and the Founder theme. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PlanGuardIT.Config.class})
class PlanGuardIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    private static final Instant BASE = Instant.parse("2026-12-01T12:00:00Z");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    OrderRepository orders;

    @Autowired
    OrderPayments payments;

    @Autowired
    PlanRepository planRows;

    @Autowired
    FakePaymentGateway gateway;

    @MockitoSpyBean
    Plans plans;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestClock clock;

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    private record Family(UUID owner, UUID ws) {}

    private Family family() {
        UUID owner = users.create("Dono", null);
        return new Family(owner, workspaces.createFor(owner));
    }

    /** An order paid at BASE, which gave the family its plan. */
    private UUID paidOrder(Family f) {
        clock.freeze(BASE);
        UUID id = orders.insert(f.ws, f.owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        orders.setCheckoutId(id, "chk_fake_" + id);
        gateway.markPaid("chk_fake_" + id, "pay_" + id);
        payments.applyPaid(id, BASE, "pay_" + id);
        return id;
    }

    private MvcTestResult getPlan(UUID user, UUID ws) {
        return mvc.get()
                .uri("/v1/workspaces/" + ws + "/billing/plan")
                .with(TestUsers.signedIn(user, null))
                .exchange();
    }

    private MvcTestResult putTheme(UUID user, UUID ws, String body) {
        var session = TestUsers.signedIn(user, null);
        return mvc.put()
                .uri("/v1/workspaces/" + ws + "/billing/plan/theme")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    @Test
    void themeWithoutPlanIs403PlanRequired() {
        Family f = family();

        MvcTestResult result = putTheme(f.owner, f.ws, "{\"theme\":\"OCEAN\"}");

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("plan.required");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Este recurso é do plano Família.");
    }

    @Test
    void themeWithPlanIs204() {
        Family f = family();
        paidOrder(f);

        assertThat(putTheme(f.owner, f.ws, "{\"theme\":\"PLUM\"}")).hasStatus(204);

        assertThat(getPlan(f.owner, f.ws)).bodyJson().extractingPath("$.theme").isEqualTo("PLUM");
    }

    @Test
    void adultThemeIs403Forbidden() {
        Family f = family();
        paidOrder(f);
        UUID adult = users.create("Bia", null);
        workspaces.addMember(f.ws, adult, Role.ADULT);

        MvcTestResult result = putTheme(adult, f.ws, "{\"theme\":\"PLUM\"}");

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
    }

    @Test
    void nonMemberIs404() {
        Family f = family();
        UUID stranger = users.create("Estranho", null);

        assertThat(putTheme(stranger, f.ws, "{\"theme\":\"PLUM\"}")).hasStatus(404);
    }

    @Test
    void invalidThemeIs400() {
        Family f = family();
        paidOrder(f);

        MvcTestResult result = putTheme(f.owner, f.ws, "{\"theme\":\"RED\"}");

        // An unknown enum value is unreadable JSON for the API: 400 request.malformed, as everywhere else.
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.malformed");
    }

    @Test
    void missingThemeIs422() {
        Family f = family();
        paidOrder(f);

        MvcTestResult result = putTheme(f.owner, f.ws, "{}");

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
    }

    @Test
    void themeResetsAfterRefund() {
        Family f = family();
        UUID order = paidOrder(f);
        assertThat(putTheme(f.owner, f.ws, "{\"theme\":\"FOREST\"}")).hasStatus(204);
        var session = TestUsers.signedIn(f.owner, null);
        assertThat(mvc.post()
                        .uri("/v1/workspaces/" + f.ws + "/billing/orders/" + order + "/refund")
                        .with(session)
                        .with(CsrfSession.start(mvc, session))
                        .exchange())
                .hasStatus(202);

        MvcTestResult result = getPlan(f.owner, f.ws);

        assertThat(result).bodyJson().extractingPath("$.key").isEqualTo("FREE");
        assertThat(result).bodyJson().extractingPath("$.theme").isEqualTo("TEAL");
        // Only the view falls back to TEAL; the stored choice stays for a later purchase.
        assertThat(planRows.find(f.ws).orElseThrow().theme()).isEqualTo("FOREST");
    }

    private static final String HINTS = "{\"items\":[{\"kind\":\"EXPENSE\",\"key\":\"padaria\"}]}";

    private MvcTestResult postHints(UUID user, UUID ws) {
        var session = TestUsers.signedIn(user, null);
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/imports/category-hints")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(HINTS)
                .exchange();
    }

    @Test
    void importOpenWhileBillingOff() {
        Family f = family();
        doReturn(false).when(plans).legalSetEnabled();

        assertThat(postHints(f.owner, f.ws)).hasStatus(200);
        assertThat(getPlan(f.owner, f.ws))
                .bodyJson()
                .extractingPath("$.importAllowed")
                .isEqualTo(true);
    }

    @Test
    void importNeedsFamilyWhileBillingOn() {
        Family f = family();
        doReturn(true).when(plans).legalSetEnabled();

        MvcTestResult free = postHints(f.owner, f.ws);
        assertThat(free).hasStatus(403);
        assertThat(free).bodyJson().extractingPath("$.code").isEqualTo("plan.required");
        assertThat(getPlan(f.owner, f.ws))
                .bodyJson()
                .extractingPath("$.importAllowed")
                .isEqualTo(false);

        paidOrder(f);

        assertThat(postHints(f.owner, f.ws)).hasStatus(200);
        assertThat(getPlan(f.owner, f.ws))
                .bodyJson()
                .extractingPath("$.importAllowed")
                .isEqualTo(true);
    }

    @Test
    void otherPlanRoutesUnchanged() {
        Family f = family();
        doReturn(false).when(plans).legalSetEnabled();

        MvcTestResult result = putTheme(f.owner, f.ws, "{\"theme\":\"OCEAN\"}");

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("plan.required");
    }
}

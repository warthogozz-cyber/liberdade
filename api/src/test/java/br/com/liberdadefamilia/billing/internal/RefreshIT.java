package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.PlanKey;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The payer's return page asks us to look at the order again. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RefreshIT.Config.class})
class RefreshIT {

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
    OrderRepository orders;

    @Autowired
    FakePaymentGateway gateway;

    @Autowired
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

    /** An open order with a checkout, as the owner's checkout leaves it. */
    private UUID openOrder(Family f) {
        UUID id = orders.insert(f.ws, f.owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        orders.setCheckoutId(id, "chk_fake_" + id);
        return id;
    }

    private MvcTestResult refresh(UUID user, UUID ws, UUID order) {
        var session = TestUsers.signedIn(user, null);
        return mvc.post()
                .uri("/v1/workspaces/" + ws + "/billing/orders/" + order + "/refresh")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
    }

    @Test
    void refreshActivatesWhenAsaasConfirms() {
        Family f = family();
        UUID order = openOrder(f);
        gateway.markPaid("chk_fake_" + order, "pay_" + order, clock.instant().minusSeconds(60));

        MvcTestResult result = refresh(f.owner, f.ws, order);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(order.toString());
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("PAID");
        assertThat(plans.current(f.ws).key()).isEqualTo(PlanKey.FAMILY);
    }

    @Test
    void refreshWhilePendingStaysCreated() {
        Family f = family();
        UUID order = openOrder(f);

        MvcTestResult result = refresh(f.owner, f.ws, order);

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CREATED");
        assertThat(plans.current(f.ws).key()).isEqualTo(PlanKey.FREE);
    }

    @Test
    void anyRoleCanRefresh() {
        Family f = family();
        UUID viewer = users.create("Vera", null);
        workspaces.addMember(f.ws, viewer, Role.VIEWER);
        UUID order = openOrder(f);

        assertThat(refresh(viewer, f.ws, order)).hasStatus(200);
    }

    @Test
    void otherFamilyOrderIsNotFound() {
        Family f = family();
        Family other = family();
        UUID foreign = openOrder(other);

        MvcTestResult result = refresh(f.owner, f.ws, foreign);

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("billing.order-not-found");
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Não encontramos esse pedido.");
    }

    @Test
    void thirtyFirstRefreshIsRateLimited() {
        // A stopped clock keeps all 31 calls in one window of the limiter.
        clock.freeze(clock.instant());
        Family f = family();
        UUID order = openOrder(f);
        for (int i = 0; i < 30; i++) {
            assertThat(refresh(f.owner, f.ws, order)).hasStatus(200);
        }

        MvcTestResult last = refresh(f.owner, f.ws, order);

        assertThat(last).hasStatus(429);
        assertThat(last).bodyJson().extractingPath("$.code").isEqualTo("rate.limited");
        assertThat(last.getResponse().getHeader("Retry-After")).isNotNull();
    }
}

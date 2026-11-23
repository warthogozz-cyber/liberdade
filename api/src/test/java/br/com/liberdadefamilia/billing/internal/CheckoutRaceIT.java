package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.billing.domain.OrderStatus;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.ZoneId;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Two tabs starting the checkout at once: both cancel the open order, then one insert meets the unique index of open
 * orders. The loser tries once more instead of answering 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CheckoutRaceIT {

    @Autowired
    CheckoutService checkout;

    @MockitoSpyBean
    OrderRepository orders;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @AfterEach
    void resetSpy() {
        Mockito.reset(orders);
    }

    private WorkspaceMember owner(UUID ws, UUID user) {
        return new WorkspaceMember(
                ws, UUID.randomUUID(), user, Role.OWNER, ZoneId.of("America/Sao_Paulo"), Currency.getInstance("BRL"));
    }

    @Test
    void theOrderTheOtherTabOpenedIsCanceledOnTheRetry() {
        UUID user = users.create("Dono Corrida", null);
        UUID ws = workspaces.createFor(user);
        // The other tab's order lands after this request's cancel ran.
        UUID other = orders.insert(ws, user, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        doReturn(0).doCallRealMethod().when(orders).cancelOpen(ws);

        CheckoutView view = checkout.start(owner(ws, user));

        verify(orders, times(2)).cancelOpen(ws);
        assertThat(orders.findById(other).orElseThrow().status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(orders.findById(view.orderId()).orElseThrow().status()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void aSecondCollisionAsksToTryAgain() {
        UUID user = users.create("Dono Corrida", null);
        UUID ws = workspaces.createFor(user);
        UUID other = orders.insert(ws, user, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        doReturn(0).when(orders).cancelOpen(ws);

        assertThatThrownBy(() -> checkout.start(owner(ws, user)))
                .isInstanceOfSatisfying(
                        BillingException.class, e -> assertThat(e.code()).isEqualTo("billing.checkout-failed"));
        assertThat(orders.findById(other).orElseThrow().status()).isEqualTo(OrderStatus.CREATED);
    }
}

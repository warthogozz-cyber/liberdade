package br.com.liberdadefamilia.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void onlyOpenStatusesCanBecomePaid() {
        List<OrderStatus> canPay = Arrays.stream(OrderStatus.values())
                .filter(OrderStatus::canBecomePaid)
                .toList();

        assertThat(canPay).containsExactlyInAnyOrder(OrderStatus.CREATED, OrderStatus.EXPIRED, OrderStatus.CANCELED);
    }
}

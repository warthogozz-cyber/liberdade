package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.support.TestUsers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The webhook and the payer's return can both report the same payment at the same moment. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderPaymentsConcurrencyIT {

    @Autowired
    OrderPayments payments;

    @Autowired
    OrderRepository orders;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Test
    void webhookAndRefreshTogether() throws Exception {
        UUID ws = jdbc.sql("insert into workspaces (name) values ('Familia Corrida') returning id")
                .query(UUID.class)
                .single();
        UUID order = orders.insert(
                ws,
                users.create("Dono Corrida", null),
                FounderOffer.PRODUCT,
                FounderOffer.PRICE_CENTS,
                FounderOffer.CURRENCY);
        Instant paidAt = Instant.now().minusSeconds(60);
        String payment = "pay_1_" + UUID.randomUUID();
        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<OrderPayments.ApplyResult>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<OrderPayments.ApplyResult> call = () -> {
                    ready.countDown();
                    go.await();
                    return payments.applyPaid(order, paidAt, payment);
                };
                results.add(pool.submit(call));
            }
            // Both threads are parked at the latch before it opens, so they start together.
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<OrderPayments.ApplyResult> outcomes = new ArrayList<>();
            for (Future<OrderPayments.ApplyResult> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(outcomes)
                    .containsExactlyInAnyOrder(
                            OrderPayments.ApplyResult.ACTIVATED, OrderPayments.ApplyResult.ALREADY_APPLIED);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.sql("select count(*) from audit_events where action = 'plan.activated' and target_id = :ws")
                        .param("ws", ws)
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }
}

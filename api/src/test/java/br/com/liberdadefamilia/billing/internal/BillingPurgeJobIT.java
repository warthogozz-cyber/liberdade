package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.domain.FounderOffer;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Every night the job removes old webhook events and orders, and reminds about refunds stuck for over 72 hours. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, BillingPurgeJobIT.Config.class})
class BillingPurgeJobIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    private static final Instant BASE = Instant.parse("2031-03-10T12:00:00Z");

    @Autowired
    BillingPurgeJob job;

    @Autowired
    OrderRepository orders;

    @Autowired
    JdbcClient jdbc;

    @MockitoSpyBean
    BillingAlerts alerts;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestClock clock;

    @BeforeEach
    void freeze() {
        jdbc.sql("delete from billing_orders").update();
        jdbc.sql("delete from billing_webhook_events").update();
        clock.freeze(BASE);
    }

    @AfterEach
    void releaseClock() {
        clock.release();
        Mockito.reset(alerts);
    }

    private static Instant daysAgo(int days) {
        return BASE.minus(Duration.ofDays(days));
    }

    /** Five years back, counted on the calendar in UTC, as the job does. */
    private static Instant fiveYearsAgo(int extraDays) {
        return BASE.atZone(ZoneOffset.UTC).minusYears(5).minusDays(extraDays).toInstant();
    }

    private UUID order(String status, Instant createdAt, Instant paidAt, Instant refundRequestedAt) {
        UUID owner = users.create("Dono Purge", null);
        UUID ws = workspaces.createFor(owner);
        UUID id = orders.insert(ws, owner, FounderOffer.PRODUCT, FounderOffer.PRICE_CENTS, FounderOffer.CURRENCY);
        jdbc.sql("""
                        update billing_orders
                           set status = :status, created_at = :createdAt, paid_at = :paidAt,
                               refund_requested_at = :refundRequestedAt
                         where id = :id""")
                .param("status", status)
                .param("createdAt", Timestamp.from(createdAt))
                .param("paidAt", paidAt == null ? null : Timestamp.from(paidAt))
                .param("refundRequestedAt", refundRequestedAt == null ? null : Timestamp.from(refundRequestedAt))
                .param("id", id)
                .update();
        return id;
    }

    private boolean orderExists(UUID id) {
        return jdbc.sql("select count(*) from billing_orders where id = :id")
                        .param("id", id)
                        .query(Integer.class)
                        .single()
                == 1;
    }

    private void event(String eventId, Instant receivedAt) {
        jdbc.sql("""
                        insert into billing_webhook_events (event_id, event_type, received_at)
                        values (:id, 'PAYMENT_RECEIVED', :at)""")
                .param("id", eventId)
                .param("at", Timestamp.from(receivedAt))
                .update();
    }

    private boolean eventExists(String eventId) {
        return jdbc.sql("select count(*) from billing_webhook_events where event_id = :id")
                        .param("id", eventId)
                        .query(Integer.class)
                        .single()
                == 1;
    }

    @Test
    void deletesOldWebhookEvents() {
        event("evt_old", daysAgo(181));
        event("evt_new", daysAgo(179));

        job.run();

        assertThat(eventExists("evt_old")).isFalse();
        assertThat(eventExists("evt_new")).isTrue();
    }

    @Test
    void deletesPaidOrdersAfterFiveYears() {
        UUID old = order("PAID", fiveYearsAgo(2), fiveYearsAgo(1), null);
        UUID recent = order("PAID", fiveYearsAgo(0), fiveYearsAgo(-1), null);

        job.run();

        assertThat(orderExists(old)).isFalse();
        assertThat(orderExists(recent)).isTrue();
    }

    @Test
    void deletesUnpaidOrdersAfter180Days() {
        UUID expired = order("EXPIRED", daysAgo(181), null, null);
        UUID canceled = order("CANCELED", daysAgo(179), null, null);

        job.run();

        assertThat(orderExists(expired)).isFalse();
        assertThat(orderExists(canceled)).isTrue();
    }

    @Test
    void oldOrderOfLivingFamilyKeepsThePlanRow() {
        UUID id = order("PAID", fiveYearsAgo(2), fiveYearsAgo(1), null);
        UUID ws = jdbc.sql("select workspace_id from billing_orders where id = :id")
                .param("id", id)
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        insert into workspace_plans (workspace_id, plan_key, founder, source_order_id, starts_at, ends_at)
                        values (:ws, 'FAMILY', true, :order, :startsAt, :endsAt)""")
                .param("ws", ws)
                .param("order", id)
                .param("startsAt", Timestamp.from(daysAgo(10)))
                .param("endsAt", Timestamp.from(BASE.plus(Duration.ofDays(300))))
                .update();

        job.run();

        assertThat(orderExists(id)).isFalse();
        assertThat(jdbc.sql("select count(*) from workspace_plans where workspace_id = :ws and source_order_id is null")
                        .param("ws", ws)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void deletesMoreThanOneBatch() {
        for (int i = 0; i < 502; i++) {
            event("evt_batch_" + i, daysAgo(200));
        }

        job.run();

        assertThat(jdbc.sql("select count(*) from billing_webhook_events")
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    @Test
    void stuckRefundAlertsDaily(CapturedOutput output) {
        order("REFUND_PENDING", daysAgo(5), daysAgo(4), BASE.minus(Duration.ofHours(73)));
        order("REFUND_PENDING", daysAgo(5), daysAgo(4), BASE.minus(Duration.ofHours(71)));
        int before = output.getAll().length();

        job.run();

        String fromTheJob = output.getAll().substring(before);
        assertThat(fromTheJob.split("BILLING_ALERT type=REFUND_STUCK", -1)).hasSize(2);
    }

    @Test
    void exactly72HoursIsNotStuck(CapturedOutput output) {
        order("REFUND_PENDING", daysAgo(5), daysAgo(4), BASE.minus(Duration.ofHours(72)));
        int before = output.getAll().length();

        job.run();

        assertThat(output.getAll().substring(before)).doesNotContain("BILLING_ALERT");
    }

    @Test
    void failureLogsOnlyTheClass(CapturedOutput output) {
        UUID stuck = order("REFUND_PENDING", daysAgo(5), daysAgo(4), BASE.minus(Duration.ofHours(80)));
        event("evt_old_failure", daysAgo(200));
        doThrow(new IllegalStateException("secret-detail")).when(alerts).raise(eq(AlertType.REFUND_STUCK));
        int before = output.getAll().length();

        job.run();

        String fromTheJob = output.getAll().substring(before);
        assertThat(fromTheJob)
                .contains("billing purge: alert step failed (" + IllegalStateException.class.getName() + ")")
                .doesNotContain("secret-detail")
                .doesNotContain(stuck.toString());
        assertThat(eventExists("evt_old_failure")).isFalse();
    }
}

package br.com.liberdadefamilia.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The plan a family is on is read against the clock: nothing stored says that it ended. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PlansIT.Config.class})
class PlansIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    private static final Instant STARTS = Instant.parse("2026-12-01T10:00:00Z");
    private static final Instant ENDS = Instant.parse("2027-12-01T10:00:00Z");

    @Autowired
    Plans plans;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestClock clock;

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    private UUID family() {
        return jdbc.sql("insert into workspaces (name) values ('Familia Plano') returning id")
                .query(UUID.class)
                .single();
    }

    private UUID familyWithPlan() {
        UUID ws = family();
        jdbc.sql("""
                        insert into workspace_plans (workspace_id, plan_key, founder, starts_at, ends_at)
                        values (:ws, 'FAMILY', true, :starts, :ends)""")
                .param("ws", ws)
                .param("starts", Timestamp.from(STARTS))
                .param("ends", Timestamp.from(ENDS))
                .update();
        return ws;
    }

    @Test
    void freeWithoutRow() {
        assertThat(plans.current(family())).isEqualTo(new PlanView(PlanKey.FREE, null, false));
    }

    @Test
    void familyWhileActive() {
        UUID ws = familyWithPlan();
        clock.freeze(ENDS.minusSeconds(1));

        assertThat(plans.current(ws)).isEqualTo(new PlanView(PlanKey.FAMILY, ENDS, true));
    }

    @Test
    void freeAfterEndsAt() {
        UUID ws = familyWithPlan();
        clock.freeze(ENDS);

        assertThat(plans.current(ws)).isEqualTo(new PlanView(PlanKey.FREE, null, false));
    }

    @Test
    void legalSetFollowsTheEnabledSwitch() {
        // The test profile turns billing on.
        assertThat(plans.legalSetEnabled()).isTrue();
    }
}

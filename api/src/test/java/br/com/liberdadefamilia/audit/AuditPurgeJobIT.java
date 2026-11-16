package br.com.liberdadefamilia.audit;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.audit.internal.AuditPurgeJob;
import br.com.liberdadefamilia.support.TestUsers;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The audit trail keeps 12 months; the nightly job removes what is older. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditPurgeJobIT {

    private static final Instant NOW = Instant.parse("2026-11-16T06:10:00Z");

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    AuditPurgeJob job;

    @Test
    void purgesOnlyWhatIsOlderThan12Months() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID old = stored(
                ana,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMonths(12).minusDays(1));
        UUID recent = stored(ana, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMonths(11));

        job.purge();

        assertThat(exists(old)).isFalse();
        assertThat(exists(recent)).isTrue();
    }

    private UUID stored(UUID actor, OffsetDateTime occurredAt) {
        return jdbc.sql("""
                        insert into audit_events (actor_user_id, action, target_type, target_id, occurred_at)
                        values (:actor, 'auth.signed_in', 'USER', :actor, :occurredAt)
                        returning id""")
                .param("actor", actor)
                .param("occurredAt", occurredAt)
                .query(UUID.class)
                .single();
    }

    private boolean exists(UUID id) {
        return jdbc.sql("select count(*) from audit_events where id = :id")
                        .param("id", id)
                        .query(Long.class)
                        .single()
                == 1;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}

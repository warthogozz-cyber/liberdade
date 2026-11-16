package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.internal.InvitationPurgeJob;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/** An invitation is kept 90 days past its last event (used, revoked or expired); then the nightly job removes it. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InvitationPurgeJobIT {

    private static final Instant NOW = Instant.parse("2026-11-16T06:15:00Z");

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    InvitationPurgeJob job;

    @Test
    void purgesOnlyWhatEndedOver90DaysAgo() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        Instant future = NOW.plus(Duration.ofDays(5));
        UUID usedLongAgo = stored(ws, future, NOW.minus(Duration.ofDays(91)), null);
        UUID usedRecently = stored(ws, future, NOW.minus(Duration.ofDays(89)), null);
        UUID revokedLongAgo = stored(ws, future, null, NOW.minus(Duration.ofDays(91)));
        UUID expiredLongAgo = stored(ws, NOW.minus(Duration.ofDays(91)), null, null);
        UUID expiredRecently = stored(ws, NOW.minus(Duration.ofDays(89)), null, null);
        UUID pending = stored(ws, future, null, null);

        job.purge();

        assertThat(exists(usedLongAgo)).isFalse();
        assertThat(exists(revokedLongAgo)).isFalse();
        assertThat(exists(expiredLongAgo)).isFalse();
        assertThat(exists(usedRecently)).isTrue();
        assertThat(exists(expiredRecently)).isTrue();
        assertThat(exists(pending)).isTrue();
    }

    private UUID stored(UUID ws, Instant expiresAt, @Nullable Instant usedAt, @Nullable Instant revokedAt) {
        return jdbc.sql("""
                        insert into invitations
                            (workspace_id, kind, role, nickname, token_hash, expires_at, used_at, revoked_at)
                        values (:ws, 'INVITE', 'VIEWER', 'Vovo', :hash, :expiresAt, :usedAt, :revokedAt)
                        returning id""")
                .param("ws", ws)
                .param("hash", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8))
                .param("expiresAt", utc(expiresAt))
                .param("usedAt", utc(usedAt))
                .param("revokedAt", utc(revokedAt))
                .query(UUID.class)
                .single();
    }

    private static @Nullable OffsetDateTime utc(@Nullable Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private boolean exists(UUID id) {
        return jdbc.sql("select count(*) from invitations where id = :id")
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

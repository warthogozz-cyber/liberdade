package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.ledger.internal.TransactionPurgeJob;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Deleted transactions stay restorable for 30 days; after that the nightly job removes them for good. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransactionPurgeJobIT {

    private static final Instant NOW = Instant.parse("2026-11-16T06:30:00Z");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    @Autowired
    TransactionPurgeJob job;

    private UUID anaId;
    private UUID workspaceId;
    private UUID membershipId;

    @BeforeEach
    void family() {
        anaId = users.create("Ana Teste", "ana@example.com");
        workspaceId = workspaces.createFor(anaId);
        membershipId = workspaces.membershipOf(workspaceId, anaId);
    }

    @Test
    void purgesOnlyWhatWasDeletedOver30DaysAgo() {
        UUID old = stored(NOW.minus(Duration.ofDays(31)));
        UUID recent = stored(NOW.minus(Duration.ofDays(29)));
        UUID active = stored(null);

        job.purge();

        assertThat(exists(old)).isFalse();
        assertThat(exists(recent)).isTrue();
        assertThat(exists(active)).isTrue();
    }

    /** A skipped occurrence stays for good: purging it would free its slot, and the generator would make it again. */
    @Test
    void purgeKeepsSkippedPlanned() {
        UUID rule = jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, interval,
                             start_on)
                        values (:ws, 'EXPENSE', 10, 'BRL', :account, :category, 'MONTHLY', 1, '2026-10-02')
                        returning id""")
                .param("ws", workspaceId)
                .param("account", ledger.walletOf(workspaceId))
                .param("category", ledger.categoryOf(workspaceId, "EXPENSE", "Mercado"))
                .query(UUID.class)
                .single();
        UUID skipped = ledger.insertPlanned(
                workspaceId,
                ledger.walletOf(workspaceId),
                ledger.categoryOf(workspaceId, "EXPENSE", "Mercado"),
                Money.brl("10.00"),
                LocalDate.of(2026, 10, 2),
                rule,
                membershipId);
        jdbc.sql("update transactions set deleted_at = :at where id = :id")
                .param("at", OffsetDateTime.ofInstant(NOW.minus(Duration.ofDays(31)), ZoneOffset.UTC))
                .param("id", skipped)
                .update();
        UUID ordinary = stored(NOW.minus(Duration.ofDays(31)));

        job.purge();

        assertThat(exists(skipped)).isTrue();
        assertThat(exists(ordinary)).isFalse();
    }

    @Test
    void purgedTransactionCannotBeRestored() {
        UUID old = stored(NOW.minus(Duration.ofDays(31)));
        job.purge();

        var result = mvc.post()
                .uri("/v1/workspaces/" + workspaceId + "/transactions/" + old + "/restore")
                .with(TestUsers.signedIn(anaId, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(anaId, null)))
                .exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("transaction.not-found");
    }

    /** An expense of Ana's written by SQL, deleted at the given instant or not deleted. */
    private UUID stored(@Nullable Instant deletedAt) {
        UUID id = TestIds.v7();
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on,
                             account_id, category_id, member_id, created_by, deleted_at)
                        values (:id, :workspaceId, 'EXPENSE', 12.34, 'BRL', '2026-10-02',
                                :accountId, :categoryId, :membershipId, :membershipId, :deletedAt)""")
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("accountId", ledger.walletOf(workspaceId))
                .param("categoryId", ledger.categoryOf(workspaceId, "EXPENSE", "Mercado"))
                .param("membershipId", membershipId)
                .param("deletedAt", deletedAt == null ? null : OffsetDateTime.ofInstant(deletedAt, ZoneOffset.UTC))
                .update();
        return id;
    }

    private boolean exists(UUID id) {
        return jdbc.sql("select count(*) from transactions where id = :id")
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

package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.internal.FamilyPurgeJob;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The nightly purge of families whose 30-day grace is over: everything of theirs goes, the audit trail stays. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FamilyPurgeJobIT.Config.class})
class FamilyPurgeJobIT {

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
    JdbcClient jdbc;

    @Autowired
    TestClock clock;

    @Autowired
    FamilyPurgeJob job;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void purgeRemovesEverything() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID membership = workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        ledger.expense(ws, membership, ledger.categoryOf(ws, "EXPENSE", "Mercado"), "9.90", LocalDate.now());
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'planning', true)")
                .param("ws", ws)
                .update();
        jdbc.sql("""
                        insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                        values (:user, :ws, 'PARENTAL', 'v1', :ana)""").param("user", bia).param("ws", ws).param("ana", ana).update();
        jdbc.sql("""
                        insert into invitations (workspace_id, kind, role, nickname, token_hash, created_by, expires_at)
                        values (:ws, 'INVITE', 'VIEWER', 'Vovo', :hash, :ana, now() + interval '1 day')""")
                .param("ws", ws)
                .param("hash", UUID.randomUUID().toString().getBytes())
                .param("ana", ana)
                .update();
        RequestPostProcessor session = TestUsers.signedIn(ana, null);
        assertThat(mvc.post()
                        .uri("/v1/workspaces/" + ws + "/deletion")
                        .with(session)
                        .with(CsrfSession.start(mvc, session))
                        .exchange())
                .hasStatus(202);

        clock.freeze(Instant.now().plus(Duration.ofDays(30)).plusSeconds(60));
        job.purge();

        for (String table : new String[] {
            "memberships", "accounts", "categories", "transactions", "invitations", "workspace_modules", "consents"
        }) {
            assertThat(jdbc.sql("select count(*) from " + table + " where workspace_id = :ws")
                            .param("ws", ws)
                            .query(Long.class)
                            .single())
                    .as(table)
                    .isZero();
        }
        assertThat(jdbc.sql("select count(*) from workspaces where id = :ws")
                        .param("ws", ws)
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(jdbc.sql("""
                                select count(*) from audit_events
                                where action = 'workspace.deletion_requested' and target_id = :ws
                                  and workspace_id is null""").param("ws", ws).query(Long.class).single()).isEqualTo(1);
        // The people are still there: only the family goes.
        assertThat(jdbc.sql("select count(*) from users where id in (:ana, :bia)")
                        .param("ana", ana)
                        .param("bia", bia)
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
    }
}

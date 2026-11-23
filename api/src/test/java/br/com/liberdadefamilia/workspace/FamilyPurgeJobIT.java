package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.shared.Money;
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
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The nightly purge of families whose 30-day grace is over: everything of theirs goes, the audit trail stays. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FamilyPurgeJobIT.Config.class})
@ExtendWith(OutputCaptureExtension.class)
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
        UUID rule = jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, interval,
                             start_on, created_by)
                        values (:ws, 'EXPENSE', 10, 'BRL', :account, :category, 'MONTHLY', 1, '2026-11-28', :by)
                        returning id""")
                .param("ws", ws)
                .param("account", ledger.walletOf(ws))
                .param("category", ledger.categoryOf(ws, "EXPENSE", "Mercado"))
                .param("by", membership)
                .query(UUID.class)
                .single();
        ledger.insertPlanned(
                ws,
                ledger.walletOf(ws),
                ledger.categoryOf(ws, "EXPENSE", "Mercado"),
                Money.brl("10.00"),
                LocalDate.of(2026, 11, 28),
                rule,
                membership);
        UUID tag = jdbc.sql(
                        "insert into tags (workspace_id, name, created_by) values (:ws, 'Escola', :by) returning id")
                .param("ws", ws)
                .param("by", membership)
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        insert into transaction_tags (workspace_id, transaction_id, tag_id)
                        select :ws, id, :tag from transactions where workspace_id = :ws limit 1""").param("ws", ws).param("tag", tag).update();
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'indicators', true)")
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
            "memberships",
            "accounts",
            "categories",
            "transactions",
            "invitations",
            "workspace_modules",
            "consents",
            "recurring_rules",
            "tags",
            "transaction_tags"
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

    @Test
    void oneFailureDoesNotStopTheOthers(CapturedOutput output) {
        UUID failing = scheduledFamily();
        UUID fine = scheduledFamily();
        // The database turns down this one family's delete, whatever the job does.
        jdbc.sql("""
                        create function block_family_purge() returns trigger language plpgsql as $$
                        begin
                            if old.id = '%s' then
                                raise exception 'boom';
                            end if;
                            return old;
                        end $$""".formatted(failing)).update();
        jdbc.sql("""
                        create trigger block_family_purge before delete on workspaces
                        for each row execute function block_family_purge()""").update();
        try {
            clock.freeze(Instant.now().plus(Duration.ofDays(30)).plusSeconds(60));
            job.purge();
        } finally {
            jdbc.sql("drop trigger block_family_purge on workspaces").update();
            jdbc.sql("drop function block_family_purge()").update();
        }

        assertThat(familyCount(failing)).isOne();
        assertThat(jdbc.sql("select count(*) from memberships where workspace_id = :ws")
                        .param("ws", failing)
                        .query(Long.class)
                        .single())
                .isOne();
        assertThat(familyCount(fine)).isZero();
        assertThat(output).contains("Could not purge family " + failing);
        assertThat(output).doesNotContain("boom");
        assertThat(output).contains("Purged 1 families past their deletion grace");
    }

    private UUID scheduledFamily() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        RequestPostProcessor session = TestUsers.signedIn(ana, null);
        assertThat(mvc.post()
                        .uri("/v1/workspaces/" + ws + "/deletion")
                        .with(session)
                        .with(CsrfSession.start(mvc, session))
                        .exchange())
                .hasStatus(202);
        return ws;
    }

    private long familyCount(UUID ws) {
        return jdbc.sql("select count(*) from workspaces where id = :ws")
                .param("ws", ws)
                .query(Long.class)
                .single();
    }
}

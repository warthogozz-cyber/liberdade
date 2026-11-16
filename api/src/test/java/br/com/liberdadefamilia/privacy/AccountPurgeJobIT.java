package br.com.liberdadefamilia.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.identity.UserDeletion;
import br.com.liberdadefamilia.privacy.internal.AccountPurgeJob;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The nightly purge of accounts whose 30-day grace is over. The clock is moved, never the stored dates. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AccountPurgeJobIT.Config.class})
class AccountPurgeJobIT {

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
    AccountPurgeJob job;

    @MockitoSpyBean
    UserDeletion deletion;

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
    void purgesAfterGrace() {
        Gone gone = leaver();
        requestDeletion(gone.bia);

        clock.freeze(Instant.now().plus(Duration.ofDays(30)).plusSeconds(60));
        job.purge();

        assertThat(count("select count(*) from users where id = :id", gone.bia)).isZero();
        assertThat(count("select count(*) from auth_identities where user_id = :id", gone.bia))
                .isZero();
        assertThat(count("select count(*) from user_entities where name = :id", gone.bia.toString()))
                .isZero();
        assertThat(count("select count(*) from user_credentials where user_entity_user_id = :id", "entity-" + gone.bia))
                .isZero();
        assertThat(count("select count(*) from spring_session where principal_name = :id", gone.bia.toString()))
                .isZero();
        var membership = jdbc.sql("select status, user_id from memberships where id = :id")
                .param("id", gone.membership)
                .query()
                .singleRow();
        assertThat(membership.get("status")).isEqualTo("REMOVED");
        assertThat(membership.get("user_id")).isNull();
        assertThat(count("select count(*) from consents where workspace_id = :id", gone.ws))
                .isZero();
        // The people who stay are untouched.
        assertThat(count("select count(*) from users where id = :id", gone.ana)).isEqualTo(1);
    }

    @Test
    void keepsTransactionsWithNickname() {
        Gone gone = leaver();
        ledger.expense(
                gone.ws, gone.membership, ledger.categoryOf(gone.ws, "EXPENSE", "Mercado"), "12.50", LocalDate.now());
        requestDeletion(gone.bia);

        clock.freeze(Instant.now().plus(Duration.ofDays(30)).plusSeconds(60));
        job.purge();

        assertThat(count("select count(*) from transactions where member_id = :id", gone.membership))
                .isEqualTo(1);
        var members = mvc.get()
                .uri("/v1/workspaces/" + gone.ws + "/members")
                .with(TestUsers.signedIn(gone.ana, null))
                .exchange();
        assertThat(members)
                .bodyJson()
                .extractingPath("$[?(@.nickname == 'Bibi')].displayName")
                .asList()
                .containsExactly((Object) null);
        assertThat(members)
                .bodyJson()
                .extractingPath("$[?(@.nickname == 'Bibi')].status")
                .asList()
                .containsExactly("REMOVED");
    }

    @Test
    void keepsAuditWithoutActor() {
        Gone gone = leaver();
        requestDeletion(gone.bia);

        clock.freeze(Instant.now().plus(Duration.ofDays(30)).plusSeconds(60));
        job.purge();

        assertThat(jdbc.sql("""
                                select count(*) from audit_events
                                where action = 'account.deletion_requested'
                                  and target_id = :id and actor_user_id is null""").param("id", gone.bia).query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void notBeforeGrace() {
        Gone gone = leaver();
        requestDeletion(gone.bia);

        clock.freeze(Instant.now().plus(Duration.ofDays(29)));
        job.purge();

        assertThat(count("select count(*) from users where id = :id", gone.bia)).isEqualTo(1);
        assertThat(count("select count(*) from user_entities where name = :id", gone.bia.toString()))
                .isEqualTo(1);
    }

    @Test
    void cancelledIsNotPurged() {
        Gone gone = leaver();
        requestDeletion(gone.bia);
        RequestPostProcessor session = TestUsers.signedIn(gone.bia, null);
        mvc.delete()
                .uri("/v1/me/deletion")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();

        clock.freeze(Instant.now().plus(Duration.ofDays(31)));
        job.purge();

        assertThat(count("select count(*) from users where id = :id", gone.bia)).isEqualTo(1);
    }

    @Test
    void cancelledBetweenTheListAndThePurgeChangesNothing() {
        Gone gone = leaver();
        requestDeletion(gone.bia);
        RequestPostProcessor session = TestUsers.signedIn(gone.bia, null);
        mvc.delete()
                .uri("/v1/me/deletion")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
        clock.freeze(Instant.now().plus(Duration.ofDays(31)));
        // The list was read while the request still stood.
        doReturn(List.of(gone.bia)).when(deletion).dueForPurge(any());

        job.purge();

        assertThat(count("select count(*) from users where id = :id", gone.bia)).isEqualTo(1);
        assertThat(count("select count(*) from memberships where id = :id and user_id is not null", gone.membership))
                .isEqualTo(1);
        assertThat(count("select count(*) from memberships where id = :id and status <> 'REMOVED'", gone.membership))
                .isEqualTo(1);
    }

    @Test
    void oneFailureDoesNotStopTheOthers() {
        Gone failing = leaver();
        Gone fine = leaver();
        requestDeletion(failing.bia);
        requestDeletion(fine.bia);
        clock.freeze(Instant.now().plus(Duration.ofDays(30)).plusSeconds(60));
        doThrow(new IllegalStateException("boom")).when(deletion).purge(failing.bia);

        job.purge();

        // The failed person is untouched, memberships included: the whole transaction rolled back.
        assertThat(count("select count(*) from users where id = :id", failing.bia))
                .isEqualTo(1);
        assertThat(count("select count(*) from memberships where id = :id and user_id is not null", failing.membership))
                .isEqualTo(1);
        assertThat(count("select count(*) from users where id = :id", fine.bia)).isZero();
    }

    // --- helpers ---

    private record Gone(UUID ana, UUID bia, UUID ws, UUID membership) {}

    /** Ana owns the family; Bia, an adult called Bibi there, has a passkey and an open session. */
    private Gone leaver() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID membership = workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
        jdbc.sql(
                        "insert into auth_identities (user_id, provider, subject, last_used_at) values (:u, 'google', :s, now())")
                .param("u", bia)
                .param("s", "sub-" + bia)
                .update();
        jdbc.sql("insert into user_entities (id, name, display_name) values (:id, :name, 'Bia Teste')")
                .param("id", "entity-" + bia)
                .param("name", bia.toString())
                .update();
        jdbc.sql("""
                        insert into user_credentials
                            (credential_id, user_entity_user_id, public_key, backup_eligible, backup_state, label)
                        values (:cred, :entity, :key, false, false, 'Celular')""")
                .param("cred", "cred-" + bia)
                .param("entity", "entity-" + bia)
                .param("key", new byte[] {1, 2, 3})
                .update();
        jdbc.sql("""
                        insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                        values (:user, :ws, 'PARENTAL', 'v1', :ana)""").param("user", bia).param("ws", ws).param("ana", ana).update();
        long now = System.currentTimeMillis();
        jdbc.sql("""
                        insert into spring_session
                            (primary_id, session_id, creation_time, last_access_time, max_inactive_interval,
                             expiry_time, principal_name)
                        values (:primary, :session, :now, :now, 3600, :expiry, :principal)""")
                .param("primary", UUID.randomUUID().toString())
                .param("session", UUID.randomUUID().toString())
                .param("now", now)
                .param("expiry", now + 3_600_000)
                .param("principal", bia.toString())
                .update();
        return new Gone(ana, bia, ws, membership);
    }

    private void requestDeletion(UUID user) {
        RequestPostProcessor session = TestUsers.signedIn(user, null);
        assertThat(mvc.post()
                        .uri("/v1/me/deletion")
                        .with(session)
                        .with(CsrfSession.start(mvc, session))
                        .exchange())
                .hasStatus(202);
    }

    private long count(String sql, Object id) {
        return jdbc.sql(sql).param("id", id).query(Long.class).single();
    }
}

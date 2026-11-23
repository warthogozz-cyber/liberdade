package br.com.liberdadefamilia.planning;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.planning.internal.RecurrenceGenerator;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.MemberRemoved;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
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

/** The generator on its own: who it plans as, that it can run again, and where it stops. Today is 2026-11-23. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RecurrenceGeneratorIT.Config.class})
class RecurrenceGeneratorIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    @Autowired
    RecurrenceGenerator generator;

    @Autowired
    Memberships memberships;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    @Autowired
    TestClock clock;

    private UUID ws;
    private UUID anaMembership;
    private UUID wallet;
    private UUID mercado;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-23T12:00:00Z"));
        UUID ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        anaMembership = workspaces.membershipOf(ws, ana);
        wallet = ledger.walletOf(ws);
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void authorFallsBackToOwner() {
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bia");
        UUID rule = insertRule(biaMembership, "2026-11-10", null);
        generator.generate(ws, rule);
        assertThat(authors(rule, "2026-11-10")).containsExactly(biaMembership + " " + biaMembership);
        memberships.remove(ws, biaMembership, null, MemberRemoved.Reason.BY_OWNER);

        clock.freeze(Instant.parse("2026-12-05T12:00:00Z"));
        generator.generate(ws, rule);

        assertThat(authors(rule, "2027-01-10")).containsExactly(anaMembership + " " + anaMembership);
        assertThat(authors(rule, "2026-12-10")).containsExactly(biaMembership + " " + biaMembership);
    }

    @Test
    void familyWithoutActiveOwnerGeneratesNothing() {
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bia");
        UUID rule = insertRule(biaMembership, "2026-11-10", null);
        memberships.remove(ws, biaMembership, null, MemberRemoved.Reason.BY_OWNER);
        memberships.remove(ws, anaMembership, null, MemberRemoved.Reason.BY_OWNER);

        generator.generate(ws, rule);

        assertThat(rowsOf(rule)).isZero();
    }

    @Test
    void runningTwiceCreatesNothing() {
        UUID rule = insertRule(anaMembership, "2026-11-10", null);

        generator.generate(ws, rule);
        int first = rowsOf(rule);
        generator.generate(ws, rule);

        assertThat(first).isEqualTo(2);
        assertThat(rowsOf(rule)).isEqualTo(first);
    }

    @Test
    void endOnMidHorizonStops() {
        UUID rule = insertRule(anaMembership, "2026-11-10", "2026-12-01");

        generator.generate(ws, rule);

        assertThat(rowsOf(rule)).isEqualTo(1);
        assertThat(nextDueOn(rule)).isNull();
    }

    @Test
    void nextDueOnMovesPastTheHorizon() {
        UUID rule = insertRule(anaMembership, "2026-11-10", null);

        generator.generate(ws, rule);

        assertThat(nextDueOn(rule)).isEqualTo(LocalDate.of(2027, 1, 10));
    }

    @Test
    void archivedRuleGeneratesNothing() {
        UUID rule = insertRule(anaMembership, "2026-11-10", null);
        jdbc.sql("update recurring_rules set archived_at = now() where id = :rule")
                .param("rule", rule)
                .update();

        generator.generate(ws, rule);

        assertThat(rowsOf(rule)).isZero();
    }

    @Test
    void archivedCategorySkipsTheOccurrenceAndGoesOn() {
        UUID rule = insertRule(anaMembership, "2026-11-10", null);
        jdbc.sql("update categories set archived_at = now() where id = :id")
                .param("id", mercado)
                .update();

        generator.generate(ws, rule);

        assertThat(rowsOf(rule)).isZero();
        assertThat(nextDueOn(rule)).isEqualTo(LocalDate.of(2027, 1, 10));
    }

    private UUID insertRule(UUID createdBy, String startOn, @Nullable String endOn) {
        return jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, interval,
                             start_on, end_on, next_due_on, created_by)
                        values (:ws, 'EXPENSE', 100, 'BRL', :account, :category, 'MONTHLY', 1,
                                cast(:startOn as date), cast(:endOn as date), cast(:startOn as date), :createdBy)
                        returning id""")
                .param("ws", ws)
                .param("account", wallet)
                .param("category", mercado)
                .param("startOn", startOn)
                .param("endOn", endOn)
                .param("createdBy", createdBy)
                .query(UUID.class)
                .single();
    }

    private int rowsOf(UUID rule) {
        return jdbc.sql("select count(*) from transactions where recurring_rule_id = :rule")
                .param("rule", rule)
                .query(Integer.class)
                .single();
    }

    private List<String> authors(UUID rule, String date) {
        return jdbc.sql("""
                        select created_by || ' ' || member_id from transactions
                        where recurring_rule_id = :rule and scheduled_on = cast(:date as date)""")
                .param("rule", rule)
                .param("date", date)
                .query(String.class)
                .list();
    }

    private @Nullable LocalDate nextDueOn(UUID rule) {
        return jdbc.sql("select next_due_on from recurring_rules where id = :rule")
                .param("rule", rule)
                .query((rs, row) -> Optional.ofNullable(rs.getObject(1, LocalDate.class)))
                .single()
                .orElse(null);
    }
}

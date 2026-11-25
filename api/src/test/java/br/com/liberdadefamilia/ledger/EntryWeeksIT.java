package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.SortedSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The weeks with entries, read in the family's zone, and the table V21 adds for the achievements. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EntryWeeksIT {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired
    EntryWeeks weeks;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    @Test
    void sundayNightCountsInTheLocalWeek() {
        Family family = family();
        entry(family, family.membershipId(), "2026-11-23T02:30:00Z", "CONFIRMED", null, false);

        assertThat(read(family)).containsExactly(LocalDate.of(2026, 11, 16));
    }

    @Test
    void mondayAfterMidnightInTheZone() {
        Family family = family();
        entry(family, family.membershipId(), "2026-11-23T03:30:00Z", "CONFIRMED", null, false);

        assertThat(read(family)).containsExactly(LocalDate.of(2026, 11, 23));
    }

    @Test
    void recurrenceDoesNotCount() {
        Family family = family();
        entry(family, family.membershipId(), "2026-11-18T15:00:00Z", "CONFIRMED", rule(family), false);

        assertThat(read(family)).isEmpty();
    }

    @Test
    void plannedDoesNotCount() {
        Family family = family();
        entry(family, family.membershipId(), "2026-11-18T15:00:00Z", "PLANNED", null, false);

        assertThat(read(family)).isEmpty();
    }

    @Test
    void deletedDoesNotCount() {
        Family family = family();
        entry(family, family.membershipId(), "2026-11-18T15:00:00Z", "CONFIRMED", null, true);

        assertThat(read(family)).isEmpty();
    }

    @Test
    void someoneElsesEntryDoesNotCount() {
        Family family = family();
        UUID other = workspaces.addMember(
                family.workspaceId(),
                users.create("Bia Teste", "bia" + UUID.randomUUID() + "@example.com"),
                Role.ADULT);
        entry(family, other, "2026-11-18T15:00:00Z", "CONFIRMED", null, false);

        assertThat(read(family)).isEmpty();
    }

    @Test
    void threeInTheSameWeekAreOne() {
        Family family = family();
        entry(family, family.membershipId(), "2026-11-17T15:00:00Z", "CONFIRMED", null, false);
        entry(family, family.membershipId(), "2026-11-19T15:00:00Z", "CONFIRMED", null, false);
        entry(family, family.membershipId(), "2026-11-21T15:00:00Z", "CONFIRMED", null, false);

        assertThat(read(family)).containsExactly(LocalDate.of(2026, 11, 16));
    }

    @Test
    void weeksComeInOrder() {
        Family family = family();
        entry(family, family.membershipId(), "2026-11-25T15:00:00Z", "CONFIRMED", null, false);
        entry(family, family.membershipId(), "2026-11-11T15:00:00Z", "CONFIRMED", null, false);

        assertThat(read(family)).containsExactly(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 23));
    }

    @Test
    void anotherFamilyDoesNotLeak() {
        Family family = family();
        Family other = family();
        entry(family, family.membershipId(), "2026-11-18T15:00:00Z", "CONFIRMED", null, false);

        assertThat(weeks.mondaysWithEntries(other.workspaceId(), family.membershipId(), ZONE))
                .isEmpty();
        assertThat(read(family)).containsExactly(LocalDate.of(2026, 11, 16));
    }

    @Test
    void v21CreatesTheAchievementsTable() {
        Family family = family();
        String sql = """
                insert into achievements (workspace_id, membership_id, achievement_key)
                values (:ws, :m, 'FIRST_TRANSACTION')
                on conflict do nothing""";

        for (int i = 0; i < 2; i++) {
            jdbc.sql(sql)
                    .param("ws", family.workspaceId())
                    .param("m", family.membershipId())
                    .update();
        }

        assertThat(jdbc.sql("select count(*) from achievements where membership_id = :m")
                        .param("m", family.membershipId())
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    private SortedSet<LocalDate> read(Family family) {
        return weeks.mondaysWithEntries(family.workspaceId(), family.membershipId(), ZONE);
    }

    private Family family() {
        UUID userId = users.create("Ana Teste", "ana" + UUID.randomUUID() + "@example.com");
        UUID workspaceId = workspaces.createFor(userId);
        return new Family(
                workspaceId,
                workspaces.membershipOf(workspaceId, userId),
                ledger.walletOf(workspaceId),
                ledger.categoryOf(workspaceId, "EXPENSE", "Mercado"));
    }

    private UUID rule(Family family) {
        return jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, interval,
                             start_on)
                        values (:ws, 'EXPENSE', 10, 'BRL', :account, :category, 'MONTHLY', 1, '2026-11-05')
                        returning id""")
                .param("ws", family.workspaceId())
                .param("account", family.wallet())
                .param("category", family.category())
                .query(UUID.class)
                .single();
    }

    /** A transaction written straight in the database, created at a fixed instant. */
    private void entry(
            Family family, UUID createdBy, String createdAt, String status, @Nullable UUID rule, boolean deleted) {
        Timestamp at = Timestamp.from(Instant.parse(createdAt));
        boolean scheduled = rule != null || status.equals("PLANNED");
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, category_id,
                             member_id, created_by, status, recurring_rule_id, scheduled_on, created_at, deleted_at)
                        values (:id, :ws, 'EXPENSE', 10, 'BRL', '2026-11-18', :account, :category,
                                :by, :by, :status, :rule, :scheduled, :createdAt, :deletedAt)""")
                .param("id", TestIds.v7())
                .param("ws", family.workspaceId())
                .param("account", family.wallet())
                .param("category", family.category())
                .param("by", createdBy)
                .param("status", status)
                .param("rule", rule)
                .param("scheduled", scheduled ? LocalDate.of(2026, 11, 18) : null)
                .param("createdAt", at)
                .param("deletedAt", deleted ? at : null)
                .update();
    }

    private record Family(UUID workspaceId, UUID membershipId, UUID wallet, UUID category) {}
}

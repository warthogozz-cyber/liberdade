package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.FamilyActivity;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Families and memberships. A membership only counts while it is ACTIVE and its family has not asked for deletion; the
 * reads that hand out access apply both rules.
 */
@Component
class WorkspaceRepository {

    private final JdbcClient jdbc;
    private final FamilyActivity activity;
    private final Clock clock;

    WorkspaceRepository(JdbcClient jdbc, FamilyActivity activity, Clock clock) {
        this.jdbc = jdbc;
        this.activity = activity;
        this.clock = clock;
    }

    /**
     * Holds a lock named after the user until the transaction ends. Two requests that create a family for the same
     * person queue here, so the second one finds the first one's family. It stands in for a unique index now that a
     * person may belong to several families, and it leaves the {@code users} table, which {@code identity} owns,
     * alone.
     */
    void lockFamilyCreation(UUID userId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:userId::text, 0))")
                .param("userId", userId)
                .query()
                .listOfRows();
    }

    /** Whether the user is an ACTIVE OWNER of a family, even one being deleted. */
    boolean isActiveOwnerOfAnyFamily(UUID userId) {
        return jdbc.sql("""
                        select exists (
                            select 1 from memberships
                            where user_id = :userId and role = 'OWNER' and status = 'ACTIVE')""").param("userId", userId).query(Boolean.class).single();
    }

    void rename(UUID workspaceId, String name) {
        jdbc.sql("update workspaces set name = :name, updated_at = now(), version = version + 1 where id = :id")
                .param("name", name)
                .param("id", workspaceId)
                .update();
    }

    NewWorkspace insert(String name, ZoneId timezone) {
        return jdbc.sql("insert into workspaces (name, timezone) values (:name, :timezone) returning id, base_currency")
                .param("name", name)
                .param("timezone", timezone.getId())
                .query((rs, row) -> new NewWorkspace(
                        rs.getObject("id", UUID.class), Currency.getInstance(rs.getString("base_currency"))))
                .single();
    }

    UUID insertOwner(UUID workspaceId, UUID userId, @Nullable String nickname) {
        return jdbc.sql("""
                        insert into memberships (workspace_id, user_id, role, nickname)
                        values (:workspaceId, :userId, :role, :nickname)
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("userId", userId)
                .param("role", Role.OWNER.name())
                .param("nickname", nickname)
                .query(UUID.class)
                .single();
    }

    /** The families the user is an active member of: one query for the memberships, one for the activity. */
    List<MeWorkspace> activeWorkspacesOf(UUID userId) {
        return meWorkspaces(userId, null);
    }

    /** One of those families, or empty when the user is not an active member of it. */
    Optional<MeWorkspace> activeWorkspaceOf(UUID userId, UUID workspaceId) {
        return meWorkspaces(userId, workspaceId).stream().findFirst();
    }

    private List<MeWorkspace> meWorkspaces(UUID userId, @Nullable UUID workspaceId) {
        List<MeRow> rows = jdbc.sql("""
                        select w.id, w.name, m.role, m.id as membership_id, w.timezone, w.base_currency,
                               m.getting_started_dismissed_at, m.recovered_at,
                               ((select count(*) from memberships o
                                 where o.workspace_id = w.id and o.status = 'ACTIVE') >= 2
                                or exists (select 1 from invitations i
                                           where i.workspace_id = w.id and i.kind = 'INVITE'
                                             and i.used_at is null and i.revoked_at is null
                                             and i.expires_at > :now)) as family_invited
                        from memberships m
                        join workspaces w on w.id = m.workspace_id
                        where m.user_id = :userId
                          and m.status = 'ACTIVE'
                          and w.deletion_requested_at is null
                          and (cast(:workspaceId as uuid) is null or w.id = cast(:workspaceId as uuid))
                        order by m.created_at, m.id""")
                .param("userId", userId)
                .param("workspaceId", workspaceId)
                .param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .query((rs, row) -> toRow(rs))
                .list();
        Set<UUID> withTransactions =
                activity.withTransactions(rows.stream().map(MeRow::id).toList());
        return rows.stream()
                .map(r -> new MeWorkspace(
                        r.id(),
                        r.name(),
                        r.role(),
                        r.membershipId(),
                        r.timezone(),
                        r.currency(),
                        r.dismissedAt(),
                        withTransactions.contains(r.id()),
                        r.familyInvited(),
                        r.recoveredAt()))
                .toList();
    }

    private static MeRow toRow(ResultSet rs) throws SQLException {
        OffsetDateTime dismissed = rs.getObject("getting_started_dismissed_at", OffsetDateTime.class);
        OffsetDateTime recovered = rs.getObject("recovered_at", OffsetDateTime.class);
        return new MeRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                Role.valueOf(rs.getString("role")),
                rs.getObject("membership_id", UUID.class),
                rs.getString("timezone"),
                rs.getString("base_currency"),
                dismissed == null ? null : dismissed.toInstant(),
                rs.getBoolean("family_invited"),
                recovered == null ? null : recovered.toInstant());
    }

    Optional<WorkspaceMember> activeMember(UUID workspaceId, UUID userId) {
        return jdbc.sql("""
                        select m.id, m.role, w.timezone, w.base_currency
                        from memberships m
                        join workspaces w on w.id = m.workspace_id
                        where m.workspace_id = :workspaceId
                          and m.user_id = :userId
                          and m.status = 'ACTIVE'
                          and w.deletion_requested_at is null""")
                .param("workspaceId", workspaceId)
                .param("userId", userId)
                .query((rs, row) -> new WorkspaceMember(
                        workspaceId,
                        rs.getObject("id", UUID.class),
                        userId,
                        Role.valueOf(rs.getString("role")),
                        ZoneId.of(rs.getString("timezone")),
                        Currency.getInstance(rs.getString("base_currency"))))
                .optional();
    }

    /**
     * Stamps the member's dismissal once: a repeat keeps the first date. Returns whether the user has an active
     * membership in that family, which is also the whole access check for this route.
     */
    boolean dismissGettingStarted(UUID workspaceId, UUID userId) {
        return jdbc.sql("""
                        update memberships m
                        set getting_started_dismissed_at = coalesce(m.getting_started_dismissed_at, now())
                        from workspaces w
                        where w.id = m.workspace_id
                          and m.workspace_id = :workspaceId
                          and m.user_id = :userId
                          and m.status = 'ACTIVE'
                          and w.deletion_requested_at is null""")
                        .param("workspaceId", workspaceId)
                        .param("userId", userId)
                        .update()
                == 1;
    }

    record NewWorkspace(UUID id, Currency baseCurrency) {}

    private record MeRow(
            UUID id,
            String name,
            Role role,
            UUID membershipId,
            String timezone,
            String currency,
            @Nullable Instant dismissedAt,
            boolean familyInvited,
            @Nullable Instant recoveredAt) {}
}

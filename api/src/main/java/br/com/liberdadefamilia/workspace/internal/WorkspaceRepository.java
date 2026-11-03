package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
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

    WorkspaceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Whether the user has an ACTIVE membership in any family, even one being deleted. */
    boolean hasActiveMembership(UUID userId) {
        return jdbc.sql("select exists (select 1 from memberships where user_id = :userId and status = 'ACTIVE')")
                .param("userId", userId)
                .query(Boolean.class)
                .single();
    }

    NewWorkspace insert(String name, ZoneId timezone) {
        return jdbc.sql("insert into workspaces (name, timezone) values (:name, :timezone) returning id, base_currency")
                .param("name", name)
                .param("timezone", timezone.getId())
                .query((rs, row) -> new NewWorkspace(
                        rs.getObject("id", UUID.class), Currency.getInstance(rs.getString("base_currency"))))
                .single();
    }

    /** Throws {@code DuplicateKeyException} when the user already has an ACTIVE membership somewhere. */
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

    List<MeWorkspace> activeWorkspacesOf(UUID userId) {
        return jdbc.sql("""
                        select w.id, w.name, m.role, m.id as membership_id, w.timezone, w.base_currency
                        from memberships m
                        join workspaces w on w.id = m.workspace_id
                        where m.user_id = :userId
                          and m.status = 'ACTIVE'
                          and w.deletion_requested_at is null
                        order by m.created_at, m.id""")
                .param("userId", userId)
                .query((rs, row) -> new MeWorkspace(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        Role.valueOf(rs.getString("role")),
                        rs.getObject("membership_id", UUID.class),
                        rs.getString("timezone"),
                        rs.getString("base_currency")))
                .list();
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

    record NewWorkspace(UUID id, Currency baseCurrency) {}
}

package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.MemberRemoved;
import br.com.liberdadefamilia.workspace.MemberSummary;
import br.com.liberdadefamilia.workspace.MembershipStatus;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcMemberships implements Memberships {

    private static final String SELECT = """
            select workspace_id, id, user_id, role, status, nickname
            from memberships""";

    private final JdbcClient jdbc;
    private final ActiveOwners activeOwners;
    private final ApplicationEventPublisher events;

    JdbcMemberships(JdbcClient jdbc, ActiveOwners activeOwners, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.activeOwners = activeOwners;
        this.events = events;
    }

    @Override
    public boolean isActive(UUID workspaceId, UUID membershipId) {
        return jdbc.sql("""
                        select exists (
                            select 1
                            from memberships
                            where workspace_id = :workspaceId
                              and id = :membershipId
                              and status = 'ACTIVE')""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Optional<MemberSummary> find(UUID workspaceId, UUID membershipId) {
        return jdbc.sql(SELECT + " where workspace_id = :workspaceId and id = :membershipId")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query((rs, row) -> summary(rs))
                .optional();
    }

    @Override
    public List<MemberSummary> all(UUID workspaceId) {
        return jdbc.sql(SELECT + " where workspace_id = :workspaceId order by created_at, id")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> summary(rs))
                .list();
    }

    @Override
    public List<MemberSummary> membershipsOf(UUID userId) {
        return jdbc.sql(SELECT + " where user_id = :userId order by created_at, id")
                .param("userId", userId)
                .query((rs, row) -> summary(rs))
                .list();
    }

    @Override
    public List<MemberSummary> membersOf(Collection<UUID> workspaceIds, Collection<UUID> userIds) {
        if (workspaceIds.isEmpty() || userIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + " where workspace_id in (:workspaceIds) and user_id in (:userIds)")
                .param("workspaceIds", workspaceIds)
                .param("userIds", userIds)
                .query((rs, row) -> summary(rs))
                .list();
    }

    @Override
    public Map<UUID, String> workspaceNames(Collection<UUID> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        jdbc.sql("select id, name from workspaces where id in (:ids)")
                .param("ids", workspaceIds)
                .query((rs, row) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("name")))
                .list()
                .forEach(entry -> names.put(entry.getKey(), entry.getValue()));
        return names;
    }

    @Override
    @Transactional
    public boolean isSoleActiveOwnerOfLiveFamily(UUID userId) {
        List<UUID> families =
                jdbc.sql("""
                        select m.workspace_id
                        from memberships m
                        join workspaces w on w.id = m.workspace_id
                        where m.user_id = :userId
                          and m.role = 'OWNER'
                          and m.status = 'ACTIVE'
                          and w.deletion_requested_at is null""").param("userId", userId).query(UUID.class).list();
        List<ActiveOwners.Owner> owners = activeOwners.lock(families);
        return families.stream().anyMatch(family -> {
            List<ActiveOwners.Owner> here = owners.stream()
                    .filter(owner -> owner.workspaceId().equals(family))
                    .toList();
            return here.stream().anyMatch(owner -> owner.userId().equals(userId))
                    && here.stream().allMatch(owner -> owner.userId().equals(userId));
        });
    }

    @Override
    @Transactional
    public void remove(UUID workspaceId, UUID membershipId, @Nullable UUID actorUserId, MemberRemoved.Reason reason) {
        int ended = jdbc.sql("""
                        update memberships
                        set status = 'REMOVED', updated_at = now(), version = version + 1
                        where workspace_id = :workspaceId and id = :membershipId and status = 'ACTIVE'""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .update();
        if (ended == 1) {
            events.publishEvent(new MemberRemoved(workspaceId, membershipId, actorUserId, reason));
        }
    }

    @Override
    @Transactional
    public boolean removeIfRole(
            UUID workspaceId,
            UUID membershipId,
            Role expected,
            @Nullable UUID actorUserId,
            MemberRemoved.Reason reason) {
        int ended = jdbc.sql("""
                        update memberships
                        set status = 'REMOVED', updated_at = now(), version = version + 1
                        where workspace_id = :workspaceId and id = :membershipId
                          and status = 'ACTIVE' and role = :expected""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .param("expected", expected.name())
                .update();
        if (ended == 1) {
            events.publishEvent(new MemberRemoved(workspaceId, membershipId, actorUserId, reason));
        }
        return ended == 1;
    }

    @Override
    @Transactional
    public void detachUser(UUID userId) {
        List<MemberSummary> ended = jdbc.sql("""
                        update memberships
                        set status = 'REMOVED', updated_at = now(), version = version + 1
                        where user_id = :userId and status = 'ACTIVE'
                        returning workspace_id, id, user_id, role, status, nickname""")
                .param("userId", userId)
                .query((rs, row) -> summary(rs))
                .list();
        ended.forEach(member -> events.publishEvent(new MemberRemoved(
                member.workspaceId(), member.membershipId(), null, MemberRemoved.Reason.ACCOUNT_DELETED)));
    }

    private static MemberSummary summary(ResultSet rs) throws SQLException {
        return new MemberSummary(
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                Role.valueOf(rs.getString("role")),
                MembershipStatus.valueOf(rs.getString("status")),
                rs.getString("nickname"));
    }
}

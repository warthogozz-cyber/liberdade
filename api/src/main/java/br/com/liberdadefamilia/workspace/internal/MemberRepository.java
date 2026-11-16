package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.MembershipStatus;
import br.com.liberdadefamilia.workspace.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's members. Every method takes the family id. */
@Component
class MemberRepository {

    private static final String SELECT = """
            select id, user_id, role, status, nickname, created_at
            from memberships""";

    private final JdbcClient jdbc;

    MemberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<MemberRow> all(UUID workspaceId) {
        return jdbc.sql(SELECT + " where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> toRow(rs))
                .list();
    }

    Optional<MemberRow> find(UUID workspaceId, UUID membershipId) {
        return jdbc.sql(SELECT + " where workspace_id = :workspaceId and id = :membershipId")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query((rs, row) -> toRow(rs))
                .optional();
    }

    /**
     * Locks the family's ACTIVE owners until the transaction ends and returns their ids. Two requests that could each
     * take away an owner queue here, and the second sees what the first left.
     */
    List<UUID> lockActiveOwners(UUID workspaceId) {
        return jdbc.sql("""
                        select id from memberships
                        where workspace_id = :workspaceId and role = 'OWNER' and status = 'ACTIVE'
                        for update""").param("workspaceId", workspaceId).query(UUID.class).list();
    }

    /** Applies what is not null. {@code consentBy}, when given, stamps the parental consent now. */
    void update(
            UUID workspaceId,
            UUID membershipId,
            @Nullable Role role,
            @Nullable String nickname,
            @Nullable UUID consentBy) {
        jdbc.sql("""
                        update memberships
                        set role = coalesce(:role, role),
                            nickname = coalesce(:nickname, nickname),
                            parental_consent_at = case when :consent then now() else parental_consent_at end,
                            parental_consent_by = case when :consent then :consentBy else parental_consent_by end,
                            updated_at = now(),
                            version = version + 1
                        where workspace_id = :workspaceId and id = :membershipId""")
                .param("role", role == null ? null : role.name())
                .param("nickname", nickname)
                .param("consent", consentBy != null)
                .param("consentBy", consentBy)
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .update();
    }

    private static MemberRow toRow(ResultSet rs) throws SQLException {
        return new MemberRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("nickname"),
                Role.valueOf(rs.getString("role")),
                MembershipStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    record MemberRow(
            UUID id,
            @Nullable UUID userId,
            @Nullable String nickname,
            Role role,
            MembershipStatus status,
            Instant joinedAt) {}
}

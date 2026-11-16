package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.InvitationKind;
import br.com.liberdadefamilia.workspace.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The family's invitations. Links are found by the SHA-256 of their token and never by the token itself; nothing here
 * logs or returns a hash. Instants come from the application clock, not from the database's.
 */
@Component
class InvitationRepository {

    private static final String SELECT_BY_HASH = """
            select i.id, i.workspace_id, w.name as workspace_name, i.kind, i.role, i.nickname,
                   i.parental_consent_at, i.parental_consent_by, i.expires_at, i.used_at, i.revoked_at, c.nickname as created_by_name,
                   i.target_membership_id, t.nickname as target_nickname, t.status as target_status
            from invitations i
            join workspaces w on w.id = i.workspace_id
            left join memberships c on c.workspace_id = i.workspace_id and c.user_id = i.created_by
            left join memberships t on t.workspace_id = i.workspace_id and t.id = i.target_membership_id
            where i.token_hash = :hash""";

    private final JdbcClient jdbc;

    InvitationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    UUID insert(
            UUID workspaceId,
            InvitationKind kind,
            Role role,
            @Nullable String nickname,
            @Nullable Instant consentAt,
            @Nullable UUID consentBy,
            byte[] tokenHash,
            UUID createdBy,
            Instant expiresAt,
            @Nullable UUID targetMembershipId) {
        return jdbc.sql("""
                        insert into invitations (workspace_id, kind, role, nickname, parental_consent_at,
                                                 parental_consent_by, token_hash, created_by, expires_at,
                                                 target_membership_id)
                        values (:workspaceId, :kind, :role, :nickname, :consentAt, :consentBy, :tokenHash,
                                :createdBy, :expiresAt, :targetMembershipId)
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("kind", kind.name())
                .param("role", role.name())
                .param("nickname", nickname)
                .param("consentAt", utc(consentAt))
                .param("consentBy", consentBy)
                .param("tokenHash", tokenHash)
                .param("createdBy", createdBy)
                .param("expiresAt", utc(expiresAt))
                .param("targetMembershipId", targetMembershipId)
                .query(UUID.class)
                .single();
    }

    /** Cancels the recovery links still pending for this member, so that only the newest one works. */
    void revokePendingRecovery(UUID workspaceId, UUID targetMembershipId, Instant now) {
        jdbc.sql("""
                        update invitations
                        set revoked_at = :now
                        where workspace_id = :workspaceId and kind = 'RECOVERY'
                          and target_membership_id = :targetMembershipId
                          and revoked_at is null and used_at is null""")
                .param("now", utc(now))
                .param("workspaceId", workspaceId)
                .param("targetMembershipId", targetMembershipId)
                .update();
    }

    /** The invitations that can still be used, oldest first. Recovery links are never listed. */
    List<InvitationView> pending(UUID workspaceId, Instant now) {
        return jdbc.sql("""
                        select i.id, i.role, i.nickname, i.kind, i.expires_at, c.nickname as created_by_name
                        from invitations i
                        left join memberships c on c.workspace_id = i.workspace_id and c.user_id = i.created_by
                        where i.workspace_id = :workspaceId
                          and i.kind = 'INVITE'
                          and i.used_at is null
                          and i.revoked_at is null
                          and i.expires_at > :now
                        order by i.created_at, i.id""")
                .param("workspaceId", workspaceId)
                .param("now", utc(now))
                .query((rs, row) -> new InvitationView(
                        rs.getObject("id", UUID.class),
                        Role.valueOf(rs.getString("role")),
                        rs.getString("nickname"),
                        InvitationKind.valueOf(rs.getString("kind")),
                        rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                        rs.getString("created_by_name"),
                        null))
                .list();
    }

    /** Looks the link up without locking it: enough to show it. */
    Optional<InvitationRow> findByHash(byte[] tokenHash) {
        return jdbc.sql(SELECT_BY_HASH)
                .param("hash", tokenHash)
                .query((rs, row) -> toRow(rs))
                .optional();
    }

    /**
     * Looks the link up and holds its row until the transaction ends. Two people taking the same link queue here, and
     * the second one finds it used.
     */
    Optional<InvitationRow> lockByHash(byte[] tokenHash) {
        return jdbc.sql(SELECT_BY_HASH + " for update of i")
                .param("hash", tokenHash)
                .query((rs, row) -> toRow(rs))
                .optional();
    }

    /** Cancels a link that is still pending. Whether this call was the one that cancelled it. */
    boolean revoke(UUID workspaceId, UUID invitationId, Instant now) {
        return jdbc.sql("""
                        update invitations
                        set revoked_at = :now
                        where workspace_id = :workspaceId and id = :invitationId
                          and revoked_at is null and used_at is null""")
                        .param("now", utc(now))
                        .param("workspaceId", workspaceId)
                        .param("invitationId", invitationId)
                        .update()
                == 1;
    }

    boolean exists(UUID workspaceId, UUID invitationId) {
        return jdbc.sql("select exists (select 1 from invitations where workspace_id = :workspaceId and id = :id)")
                .param("workspaceId", workspaceId)
                .param("id", invitationId)
                .query(Boolean.class)
                .single();
    }

    void markUsed(UUID invitationId, Instant now) {
        jdbc.sql("update invitations set used_at = :now where id = :id")
                .param("now", utc(now))
                .param("id", invitationId)
                .update();
    }

    private static @Nullable OffsetDateTime utc(@Nullable Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static InvitationRow toRow(ResultSet rs) throws SQLException {
        OffsetDateTime used = rs.getObject("used_at", OffsetDateTime.class);
        OffsetDateTime revoked = rs.getObject("revoked_at", OffsetDateTime.class);
        OffsetDateTime consentAt = rs.getObject("parental_consent_at", OffsetDateTime.class);
        return new InvitationRow(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("workspace_name"),
                InvitationKind.valueOf(rs.getString("kind")),
                Role.valueOf(rs.getString("role")),
                rs.getString("nickname"),
                consentAt == null ? null : consentAt.toInstant(),
                rs.getObject("parental_consent_by", UUID.class),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                used != null,
                revoked != null,
                rs.getString("created_by_name"),
                rs.getObject("target_membership_id", UUID.class),
                rs.getString("target_nickname"),
                "ACTIVE".equals(rs.getString("target_status")));
    }

    record InvitationRow(
            UUID id,
            UUID workspaceId,
            String workspaceName,
            InvitationKind kind,
            Role role,
            @Nullable String nickname,
            @Nullable Instant consentAt,
            @Nullable UUID consentBy,
            Instant expiresAt,
            boolean used,
            boolean revoked,
            @Nullable String createdByName,
            @Nullable UUID targetMembershipId,
            @Nullable String targetNickname,
            boolean targetActive) {

        /** Used, cancelled or past its time: what a person who holds the link can no longer do with it. */
        boolean isGone(Instant now) {
            return used || revoked || !expiresAt.isAfter(now);
        }
    }
}

package br.com.liberdadefamilia.privacy.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The consents table. */
@Component
class ConsentRepository {

    /** One stored consent. */
    record Row(
            UUID id,
            UUID userId,
            UUID workspaceId,
            ConsentPurpose purpose,
            String documentVersion,
            @Nullable UUID grantedBy,
            Instant grantedAt,
            @Nullable Instant revokedAt,
            @Nullable Instant supersededAt) {

        /** Neither withdrawn by the guardian nor replaced by a newer consent. */
        boolean open() {
            return revokedAt == null && supersededAt == null;
        }
    }

    private static final String COLUMNS =
            "id, user_id, workspace_id, purpose, document_version, granted_by, granted_at, revoked_at, superseded_at";

    private final JdbcClient jdbc;

    ConsentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(
            UUID userId, UUID workspaceId, ConsentPurpose purpose, String documentVersion, @Nullable UUID grantedBy) {
        jdbc.sql("""
                        insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                        values (:userId, :workspaceId, :purpose, :documentVersion, :grantedBy)""")
                .param("userId", userId)
                .param("workspaceId", workspaceId)
                .param("purpose", purpose.name())
                .param("documentVersion", documentVersion)
                .param("grantedBy", grantedBy)
                .update();
    }

    /** Closes the open consents of the person in the family for the purpose: a newer one is about to take over. */
    void supersedeOpen(UUID userId, UUID workspaceId, ConsentPurpose purpose) {
        jdbc.sql("""
                        update consents set superseded_at = now()
                        where user_id = :userId and workspace_id = :workspaceId and purpose = :purpose
                          and revoked_at is null and superseded_at is null""")
                .param("userId", userId)
                .param("workspaceId", workspaceId)
                .param("purpose", purpose.name())
                .update();
    }

    /** Consents about me or given by me, revoked ones included, newest first. */
    List<Row> involving(UUID userId) {
        return jdbc.sql("select " + COLUMNS + """
                         from consents
                        where user_id = :userId or granted_by = :userId
                        order by granted_at desc, id desc""")
                .param("userId", userId)
                .query(ConsentRepository::map)
                .list();
    }

    /** The consents of these families that are neither revoked nor superseded. */
    List<Row> openIn(Collection<UUID> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select " + COLUMNS + """
                         from consents
                        where workspace_id in (:workspaceIds) and revoked_at is null and superseded_at is null""")
                .param("workspaceIds", workspaceIds)
                .query(ConsentRepository::map)
                .list();
    }

    /** Locks the row for the rest of the transaction, so two revocations cannot both act. */
    Optional<Row> lock(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from consents where id = :id for update")
                .param("id", id)
                .query(ConsentRepository::map)
                .optional();
    }

    void markRevoked(UUID id) {
        jdbc.sql("update consents set revoked_at = now() where id = :id and revoked_at is null")
                .param("id", id)
                .update();
    }

    private static Row map(ResultSet rs, int row) throws SQLException {
        OffsetDateTime revoked = rs.getObject("revoked_at", OffsetDateTime.class);
        OffsetDateTime superseded = rs.getObject("superseded_at", OffsetDateTime.class);
        return new Row(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                ConsentPurpose.valueOf(rs.getString("purpose")),
                rs.getString("document_version"),
                rs.getObject("granted_by", UUID.class),
                rs.getObject("granted_at", OffsetDateTime.class).toInstant(),
                revoked == null ? null : revoked.toInstant(),
                superseded == null ? null : superseded.toInstant());
    }
}

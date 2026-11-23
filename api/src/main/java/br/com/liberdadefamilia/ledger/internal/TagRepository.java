package br.com.liberdadefamilia.ledger.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's tags and their links to transactions. Every query is for one family. */
@Component
class TagRepository {

    private final JdbcClient jdbc;

    TagRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The active tags a member may see, by name: every one for the unrestricted scope; for the youth, the ones they
     * created and the ones on a live transaction in their scope.
     */
    List<TagView> listActive(UUID workspaceId, UUID membershipId, AccountScope scope) {
        return jdbc.sql("""
                        select t.id, t.name, t.archived_at
                        from tags t
                        where t.workspace_id = :workspaceId
                          and t.archived_at is null
                          and (cast(:scopeIds as uuid[]) is null
                               or t.created_by = :membershipId
                               or exists (select 1
                                          from transaction_tags tt
                                          join transactions x
                                            on x.workspace_id = tt.workspace_id and x.id = tt.transaction_id
                                          where tt.workspace_id = t.workspace_id
                                            and tt.tag_id = t.id
                                            and x.deleted_at is null
                                            and """ + AccountScope.sqlTouches("x.account_id", "x.to_account_id") + """
                               ))
                        order by lower(t.name), t.id""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .param("scopeIds", scope.sqlArray())
                .query(TagRepository::view)
                .list();
    }

    Optional<TagView> findActive(UUID workspaceId, UUID id) {
        return jdbc.sql("""
                        select id, name, archived_at from tags
                        where workspace_id = :workspaceId and id = :id and archived_at is null""")
                .param("workspaceId", workspaceId)
                .param("id", id)
                .query(TagRepository::view)
                .optional();
    }

    /** The id of the active tag with this name, letter case aside. */
    Optional<UUID> findActiveIdByName(UUID workspaceId, String name) {
        return jdbc.sql("""
                        select id from tags
                        where workspace_id = :workspaceId and lower(name) = lower(cast(:name as text))
                          and archived_at is null""")
                .param("workspaceId", workspaceId)
                .param("name", name)
                .query(UUID.class)
                .optional();
    }

    long countActive(UUID workspaceId) {
        return jdbc.sql("select count(*) from tags where workspace_id = :workspaceId and archived_at is null")
                .param("workspaceId", workspaceId)
                .query(Long.class)
                .single();
    }

    /** Makes the tag; empty when an active tag already has the name (another request made it first). */
    Optional<UUID> insertIfAbsent(UUID workspaceId, String name, UUID createdBy) {
        return jdbc.sql("""
                        insert into tags (workspace_id, name, created_by)
                        values (:workspaceId, :name, :createdBy)
                        on conflict do nothing
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("name", name)
                .param("createdBy", createdBy)
                .query(UUID.class)
                .optional();
    }

    /** Renames the active tag. Whether there was one; a taken name is a {@code DuplicateKeyException}. */
    boolean rename(UUID workspaceId, UUID id, String name) {
        return jdbc.sql("""
                                update tags set name = :name
                                where workspace_id = :workspaceId and id = :id and archived_at is null""")
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .param("name", name)
                        .update()
                == 1;
    }

    /** Archives the tag, keeping the time of an earlier archive. Whether the family has the tag, archived or not. */
    boolean archive(UUID workspaceId, UUID id, Instant now) {
        return jdbc.sql("""
                                update tags set archived_at = coalesce(archived_at, :now)
                                where workspace_id = :workspaceId and id = :id""")
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                        .update()
                == 1;
    }

    /** Makes the transaction's tags exactly these. */
    void replaceLinks(UUID workspaceId, UUID transactionId, List<UUID> tagIds) {
        jdbc.sql("delete from transaction_tags where workspace_id = :workspaceId and transaction_id = :transactionId")
                .param("workspaceId", workspaceId)
                .param("transactionId", transactionId)
                .update();
        for (UUID tagId : tagIds) {
            jdbc.sql("""
                            insert into transaction_tags (workspace_id, transaction_id, tag_id)
                            values (:workspaceId, :transactionId, :tagId)""")
                    .param("workspaceId", workspaceId)
                    .param("transactionId", transactionId)
                    .param("tagId", tagId)
                    .update();
        }
    }

    /** The names of the transaction's tags, archived ones too, by name. */
    List<String> namesOf(UUID workspaceId, UUID transactionId) {
        return jdbc.sql("""
                        select t.name
                        from transaction_tags tt
                        join tags t on t.workspace_id = tt.workspace_id and t.id = tt.tag_id
                        where tt.workspace_id = :workspaceId and tt.transaction_id = :transactionId
                        order by lower(t.name), t.id""")
                .param("workspaceId", workspaceId)
                .param("transactionId", transactionId)
                .query(String.class)
                .list();
    }

    private static TagView view(ResultSet rs, int row) throws SQLException {
        OffsetDateTime archivedAt = rs.getObject("archived_at", OffsetDateTime.class);
        return new TagView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                archivedAt == null ? null : archivedAt.toInstant());
    }
}

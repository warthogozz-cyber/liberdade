package br.com.liberdadefamilia.ledger.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Statement import keys: the ones on transactions (deleted ones included) and the tombstones of purged ones. */
@Repository
class ImportRepository {

    private final JdbcClient jdbc;

    ImportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Whether a transaction (deleted or not) or a tombstone of the family already holds the key. */
    boolean keyTaken(UUID workspaceId, String importKey) {
        return jdbc.sql("""
                        select exists (select 1 from transactions where workspace_id = :ws and import_key = :key)
                            or exists (select 1 from import_tombstones where workspace_id = :ws and import_key = :key)""")
                .param("ws", workspaceId)
                .param("key", importKey)
                .query(Boolean.class)
                .single();
    }

    /** Removes the tombstones purged before the cutoff and answers how many went. */
    int purgeTombstonesBefore(Instant cutoff) {
        return jdbc.sql("delete from import_tombstones where purged_at < :cutoff")
                .param("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .update();
    }
}

package br.com.liberdadefamilia.markets.internal;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's {@code receipts}. Every query filters by the family (ADR-0008). */
@Component
class ReceiptRepository {

    private final JdbcClient jdbc;

    ReceiptRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Whether the family already saved the receipt with this key hash. */
    boolean existsByHash(UUID workspaceId, String keyHash) {
        return jdbc.sql("select exists (select 1 from receipts where workspace_id = :ws and key_hash = :hash)")
                .param("ws", workspaceId)
                .param("hash", keyHash)
                .query(Boolean.class)
                .single();
    }

    /**
     * Waits for any other save of the same key in this family, until this transaction ends. It makes the loser of two
     * saves at once see the winner's receipt and answer 409 {@code receipt.duplicate}.
     */
    void lockKey(UUID workspaceId, String keyHash) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", workspaceId + "|" + keyHash)
                .query()
                .singleRow();
    }

    /** The new receipt's id. Raises the unique violation of {@code receipts_workspace_id_key_hash_key} on a repeat. */
    UUID insert(
            UUID workspaceId,
            UUID marketId,
            String keyHash,
            LocalDate issuedOn,
            String mode,
            int itemCount,
            UUID createdBy) {
        return jdbc.sql("""
                        insert into receipts (workspace_id, market_id, key_hash, issued_on, mode, item_count, created_by)
                        values (:ws, :market, :hash, :issuedOn, :mode, :count, :actor)
                        returning id""")
                .param("ws", workspaceId)
                .param("market", marketId)
                .param("hash", keyHash)
                .param("issuedOn", issuedOn)
                .param("mode", mode)
                .param("count", itemCount)
                .param("actor", createdBy)
                .query(UUID.class)
                .single();
    }
}

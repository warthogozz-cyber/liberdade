package br.com.liberdadefamilia.markets.internal;

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
}

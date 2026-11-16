package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.workspace.FamilyActivity;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Answers the workspace's questions about the family's activity from the ledger's own tables. */
@Component
class LedgerFamilyActivity implements FamilyActivity {

    private final JdbcClient jdbc;

    LedgerFamilyActivity(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasTransactions(UUID workspaceId) {
        return jdbc.sql("""
                        select exists (
                            select 1 from transactions
                            where workspace_id = :workspaceId and deleted_at is null)""")
                .param("workspaceId", workspaceId)
                .query(Boolean.class)
                .single();
    }
}

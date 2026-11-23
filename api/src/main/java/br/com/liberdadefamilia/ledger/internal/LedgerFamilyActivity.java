package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.workspace.FamilyActivity;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
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
    public Set<UUID> withTransactions(Collection<UUID> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("""
                        select distinct workspace_id
                        from transactions
                        where workspace_id in (:workspaceIds)
                          and deleted_at is null
                          and status = 'CONFIRMED'""")
                .param("workspaceIds", workspaceIds)
                .query(UUID.class)
                .list());
    }
}

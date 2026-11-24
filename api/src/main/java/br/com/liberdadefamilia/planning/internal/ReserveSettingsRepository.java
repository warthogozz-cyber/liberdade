package br.com.liberdadefamilia.planning.internal;

import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The two reserve settings the family keeps on its workspace row. The only writer of those columns. */
@Component
class ReserveSettingsRepository {

    private final JdbcClient jdbc;

    ReserveSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Settings(int targetMonths, @Nullable BigDecimal manualMonthlyCost) {}

    Settings find(UUID workspaceId) {
        return jdbc.sql("select reserve_target_months, manual_monthly_cost from workspaces where id = :ws")
                .param("ws", workspaceId)
                .query((rs, row) ->
                        new Settings(rs.getInt("reserve_target_months"), rs.getBigDecimal("manual_monthly_cost")))
                .single();
    }

    void update(UUID workspaceId, int targetMonths, @Nullable BigDecimal manualMonthlyCost) {
        jdbc.sql("""
                        update workspaces
                        set reserve_target_months = :target, manual_monthly_cost = :cost, updated_at = now()
                        where id = :ws""")
                .param("ws", workspaceId)
                .param("target", targetMonths)
                .param("cost", manualMonthlyCost)
                .update();
    }
}

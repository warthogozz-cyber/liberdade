package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.planning.PlanningFacts;
import br.com.liberdadefamilia.planning.domain.BudgetState;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The planning facts, read from the goals and limits tables and from the reserve and budget services. */
@Service
@Transactional(readOnly = true)
class PlanningFactsService implements PlanningFacts {

    private final JdbcClient jdbc;
    private final ReserveService reserve;
    private final BudgetService budgets;

    PlanningFactsService(JdbcClient jdbc, ReserveService reserve, BudgetService budgets) {
        this.jdbc = jdbc;
        this.reserve = reserve;
        this.budgets = budgets;
    }

    @Override
    public boolean createdAGoal(UUID workspaceId, UUID membershipId) {
        return jdbc.sql("""
                        select exists (
                            select 1 from goals where workspace_id = :workspaceId and created_by = :membershipId)""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public boolean anyGoalReached(UUID workspaceId) {
        return jdbc.sql("""
                        select exists (
                            select 1 from goals where workspace_id = :workspaceId and achieved_at is not null)""")
                .param("workspaceId", workspaceId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Optional<BigDecimal> reserveMonths(WorkspaceMember member) {
        return Optional.ofNullable(reserve.view(member).months());
    }

    @Override
    public Optional<Boolean> withinBudget(WorkspaceMember member, YearMonth month) {
        List<BudgetLineView> lines = budgets.month(member, month).lines();
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(lines.stream().noneMatch(line -> line.state() == BudgetState.OVER));
    }

    @Override
    public List<UUID> familiesWithBudgets(YearMonth month) {
        return jdbc.sql("select distinct workspace_id from budgets where month = :month")
                .param("month", month.atDay(1))
                .query(UUID.class)
                .list();
    }
}

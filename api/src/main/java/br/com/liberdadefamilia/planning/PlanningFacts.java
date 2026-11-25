package br.com.liberdadefamilia.planning;

import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** What the achievements need to know about the family's planning, and nothing more. */
public interface PlanningFacts {

    /** Whether the member created a goal, archived or not. */
    boolean createdAGoal(UUID workspaceId, UUID membershipId);

    /** Whether any goal of the family was reached, archived or not. */
    boolean anyGoalReached(UUID workspaceId);

    /** The months of cost the reserve covers, as the reserve screen shows; empty while the cost is unknown. */
    Optional<BigDecimal> reserveMonths(WorkspaceMember member);

    /** Whether no limit of the month is passed ({@code AT} counts as within); empty when the month has no limits. */
    Optional<Boolean> withinBudget(WorkspaceMember member, YearMonth month);

    /** The families that set at least one limit for the month. */
    List<UUID> familiesWithBudgets(YearMonth month);
}

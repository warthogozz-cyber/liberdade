package br.com.liberdadefamilia.planning;

import java.util.List;
import java.util.UUID;

/** What planning hands over for a data export. */
public interface PlanningExport {

    /** Every recurring rule of the family, archived ones included, by start date. */
    List<ExportedRecurringRule> rules(UUID workspaceId);

    /** The recurring rules the membership created, archived ones included, by start date. */
    List<ExportedRecurringRule> rulesCreatedBy(UUID workspaceId, UUID membershipId);

    /** Every monthly limit of the family, by month. */
    List<ExportedBudget> budgets(UUID workspaceId);

    /** Every goal of the family, archived ones included, by creation. */
    List<ExportedGoal> goals(UUID workspaceId);

    /** Every contribution to the goals of the family, by date. */
    List<ExportedContribution> contributions(UUID workspaceId);

    /** The contributions the membership made, by date. */
    List<ExportedContribution> contributionsBy(UUID workspaceId, UUID membershipId);

    /** The reserve settings of the family. */
    ExportedReserveSettings reserveSettings(UUID workspaceId);
}

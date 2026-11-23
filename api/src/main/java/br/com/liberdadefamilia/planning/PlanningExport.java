package br.com.liberdadefamilia.planning;

import java.util.List;
import java.util.UUID;

/** What planning hands over for a data export. */
public interface PlanningExport {

    /** Every recurring rule of the family, archived ones included, by start date. */
    List<ExportedRecurringRule> rules(UUID workspaceId);
}

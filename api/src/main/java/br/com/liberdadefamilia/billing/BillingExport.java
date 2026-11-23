package br.com.liberdadefamilia.billing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** What billing hands over for a data export: the family's orders and the plan as stored. */
public interface BillingExport {

    /** Every order of the family, oldest first. Gateway ids never leave. */
    List<ExportedOrder> orders(UUID workspaceId);

    /** The plan row as stored, ended or not; empty when the family never had one. */
    Optional<ExportedPlan> plan(UUID workspaceId);
}

package br.com.liberdadefamilia.ventures;

import java.util.List;
import java.util.UUID;

/** What ventures hands over for a data export. */
public interface VenturesExport {

    /** Every project of the family, closed ones included, by creation, with its items, products and steps. */
    List<ExportedVenture> ventures(UUID workspaceId);

    /** The projects the membership created, by creation, with their items, products and steps. */
    List<ExportedVenture> venturesCreatedBy(UUID workspaceId, UUID membershipId);
}

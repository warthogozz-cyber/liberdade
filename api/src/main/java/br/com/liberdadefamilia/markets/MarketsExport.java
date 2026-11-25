package br.com.liberdadefamilia.markets;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** What markets hands over for a data export: the family's own rows, never what OpenStreetMap knows. */
public interface MarketsExport {

    /** Every market row of the family, archived ones included, by creation. */
    List<ExportedMarket> familyMarkets(UUID workspaceId);

    /** The address the family's markets are searched from, if it set one. */
    Optional<ExportedMarketHome> home(UUID workspaceId);
}

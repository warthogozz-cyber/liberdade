package br.com.liberdadefamilia.markets;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** What markets hands over for a data export: the family's own rows, never what OpenStreetMap knows. */
public interface MarketsExport {

    /** Every market row of the family, archived ones included, by creation. */
    List<ExportedMarket> familyMarkets(UUID workspaceId);

    /** The family's products, by creation. */
    List<ExportedProduct> products(UUID workspaceId);

    /** The family's product aliases, by market and description. */
    List<ExportedAlias> aliases(UUID workspaceId);

    /** Every price the family recorded, oldest first. */
    List<ExportedPrice> prices(UUID workspaceId);

    /** The family's receipts, by creation; the access key and its hash stay out. */
    List<ExportedReceipt> receipts(UUID workspaceId);

    /** The family's shopping list, in the order the family set. */
    List<ExportedListItem> shoppingList(UUID workspaceId);

    /** The pairs of products the family said are not the same, by id. */
    List<ExportedDismissal> joinDismissals(UUID workspaceId);

    /** The address the family's markets are searched from, if it set one. */
    Optional<ExportedMarketHome> home(UUID workspaceId);
}

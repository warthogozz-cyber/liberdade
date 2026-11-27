package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.MarketKind;
import br.com.liberdadefamilia.markets.domain.ProductUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What the Feira's routes ask and answer. Coordinates travel as decimal text ({@code "-10.00012"}), never as floating
 * point numbers.
 */
final class MarketViews {

    /** A quantity as text: up to 15 digits, and up to 4 places after the point. What the list and receipts accept. */
    static final String QUANTITY_PATTERN = "^\\d{1,15}(\\.\\d{1,4})?$";

    private MarketViews() {}

    /** Where the family's market comes from. */
    enum Origin {
        OSM,
        FAMILY
    }

    /** {@code LEFT_OSM}: the object is gone from OpenStreetMap; {@code ARCHIVED}: the family hid it. */
    enum Status {
        ACTIVE,
        LEFT_OSM,
        ARCHIVED
    }

    /** Whose link or date it is. */
    enum Source {
        FAMILY,
        OSM
    }

    /** A search: the center with at most 2 decimals, and a radius of 1 to 10 km (3 when absent). */
    record SearchRequest(
            @NotNull @Schema(example = "-10.00") String lat,
            @NotNull @Schema(example = "-50.00") String lon,

            @Nullable @Schema(nullable = true, minimum = "1", maximum = "10")
            Integer radiusKm) {}

    /** The markets around a point, in separate lists. {@code datasetDate} is the date of the OpenStreetMap data. */
    record SearchResultView(
            List<OsmItem> osm,
            List<FamilyItem> family,
            List<UnlocatedItem> unlocated,
            List<LeftItem> left,
            @Nullable @Schema(nullable = true) LocalDate datasetDate) {}

    /** A market of the OpenStreetMap extract. {@code familyId} is the family's own row, once there is one. */
    record OsmItem(
            String ref,
            MarketKind kind,
            @Nullable @Schema(nullable = true) String name,
            @Nullable @Schema(nullable = true) String brand,
            String lat,
            String lon,
            @Nullable @Schema(nullable = true) String street,
            @Nullable @Schema(nullable = true) String housenumber,
            @Nullable @Schema(nullable = true) String suburb,
            @Nullable @Schema(nullable = true) String city,
            @Nullable @Schema(nullable = true) UUID familyId) {}

    /** A market the family added, with a position. */
    record FamilyItem(
            String ref,
            String name,
            @Nullable @Schema(nullable = true) MarketKind kind,
            String lat,
            String lon) {}

    /** A market the family added without a position. */
    record UnlocatedItem(
            String ref,
            String name,
            @Nullable @Schema(nullable = true) MarketKind kind) {}

    /** A market of the family that is no longer in OpenStreetMap. */
    record LeftItem(
            String ref, @Nullable @Schema(nullable = true) String cnpj) {}

    /** The street address of an OpenStreetMap market. */
    record AddressView(
            @Nullable @Schema(nullable = true) String street,
            @Nullable @Schema(nullable = true) String housenumber,
            @Nullable @Schema(nullable = true) String suburb,
            @Nullable @Schema(nullable = true) String city) {}

    /** A website or Instagram link and whose it is; {@code date} is when the family set it, or the extract's date. */
    record LinkView(
            String value,
            Source source,
            @Nullable @Schema(nullable = true) LocalDate date) {}

    /** The flyer the family linked. */
    record FlyerView(
            String url,
            @Nullable @Schema(nullable = true) String source,
            @Nullable @Schema(nullable = true) LocalDate validUntil,
            @Nullable @Schema(nullable = true) Instant setAt) {}

    /** One market: what OpenStreetMap says about it, with the family's links on top. */
    record MarketCardView(
            String ref,
            @Nullable @Schema(nullable = true) UUID familyId,
            Origin origin,
            Status status,
            @Nullable @Schema(nullable = true) MarketKind kind,
            @Nullable @Schema(nullable = true) String name,
            @Nullable @Schema(nullable = true) String lat,
            @Nullable @Schema(nullable = true) String lon,
            @Nullable @Schema(nullable = true) AddressView address,
            @Nullable @Schema(nullable = true) String openingHours,
            @Nullable @Schema(nullable = true) String cnpj,
            @Nullable @Schema(nullable = true) LocalDate datasetDate,
            @Nullable @Schema(nullable = true) LinkView website,
            @Nullable @Schema(nullable = true) LinkView instagram,
            boolean instagramEmbeddable,
            @Nullable @Schema(nullable = true) FlyerView flyer,
            @Nullable @Schema(nullable = true) Instant updatedAt) {}

    /** The family's address: a point with 3 decimals and a name the person chose. */
    record HomeView(
            String lat,
            String lon,
            @Nullable @Schema(nullable = true) String label) {}

    /** The family's address: both coordinates with at most 3 decimals; the label has up to 40 characters. */
    record HomeRequest(
            @Nullable @Schema(nullable = true, example = "-23.123")
            String lat,

            @Nullable @Schema(nullable = true, example = "-46.123")
            String lon,

            @Nullable @Schema(nullable = true, maxLength = 40, example = "Casa")
            String label) {}

    /** An address to look for, 1 to 120 characters. */
    record GeocodeRequest(
            @NotNull @Schema(maxLength = 120, example = "Rua Exemplo 10")
            String query) {}

    /** A place the address search found, rounded to 3 decimals. */
    record PlaceView(String lat, String lon, String label) {}

    /** The places found, at most 5. */
    record GeocodeResultView(List<PlaceView> places) {}

    /** The embed HTML of an Instagram post: a blockquote without a script. */
    record InstagramEmbedView(
            @Schema(example = "<blockquote class=\"instagram-media\"></blockquote>")
            String html) {}

    /** The switches the app reads to hide what is off. */
    record SettingsView(boolean geocodingEnabled, boolean instagramEnabled) {}

    /** The last price of a product at a market; {@code unitPrice} is decimal text with four places. */
    record PriceView(
            UUID productId,
            String name,
            String unit,
            @Schema(example = "4.9900") String unitPrice,
            LocalDate observedOn) {}

    /** The last prices of a market, by product name. */
    record PricesView(List<PriceView> prices) {}

    /** A product of the family. */
    record FamilyProductView(
            UUID id,
            String name,
            String unit,
            @Nullable @Schema(nullable = true) String gtin) {}

    /** The last price of a product at one market; {@code unitPrice} is decimal text with four places. */
    record ProductPriceView(
            String marketRef,
            @Nullable @Schema(nullable = true) String marketName,
            @Schema(example = "4.9900") String unitPrice,
            ProductUnit unit,
            LocalDate observedOn) {}

    /** A product with its last price at each market. */
    record ProductOverview(
            UUID id,
            String name,
            ProductUnit unit,
            @Nullable @Schema(nullable = true) String gtin,
            List<ProductPriceView> prices) {}

    /** The family's products by name, up to 500; {@code truncated} when there are more. */
    record ProductsOverview(List<ProductOverview> products, boolean truncated) {}

    /** What to change in a product; a field left out stays as it is. */
    record EditProductRequest(
            @Nullable @Schema(nullable = true, minLength = 1, maxLength = 80)
            String name,

            @Nullable @Schema(nullable = true) ProductUnit unit) {}

    /** The product to fold into the one in the path. */
    record MergeProductsRequest(@NotNull UUID removeId) {}

    /** The family's products, by name, up to 50. */
    record FamilyProductsView(List<FamilyProductView> products) {}

    /** A product in a suggested join. */
    record SuggestedProduct(UUID id, String name, ProductUnit unit) {}

    /** Two products that may be the same; {@code a} has the smaller id and {@code score} runs from 0.5 to 1. */
    record SuggestedPair(SuggestedProduct a, SuggestedProduct b, double score) {}

    /** The pairs worth a look, best first, up to 20. */
    record SuggestionsView(List<SuggestedPair> pairs) {}

    /** Two products the family says are not the same. */
    record DismissPairRequest(@NotNull UUID a, @NotNull UUID b) {}

    /**
     * An item of the shopping list, with the product's name and unit as they are now; {@code quantity} is decimal
     * text and {@code prices} is the last price at each market, as in the products overview.
     */
    record ListItemView(
            UUID id,
            UUID productId,
            String name,
            ProductUnit unit,

            @Schema(example = "2.5", pattern = QUANTITY_PATTERN)
            String quantity,

            boolean checked,
            int position,
            List<ProductPriceView> prices) {}

    /** The family's shopping list, in the order the family set. */
    record ShoppingListView(List<ListItemView> items) {}

    /**
     * What to add: a product of the family, or the name of a new one (1 to 80). {@code quantity} is decimal text, up to
     * 15 digits and 4 places, above zero; it defaults to 1.
     */
    record AddListItemRequest(
            @Nullable @Schema(nullable = true) UUID productId,

            @Nullable @Schema(nullable = true, minLength = 1, maxLength = 80)
            String newProductName,

            @Nullable @Schema(nullable = true, example = "1", pattern = QUANTITY_PATTERN)
            String quantity) {}

    /** What to change in an item; a field left out stays as it is. */
    record UpdateListItemRequest(
            @Nullable @Schema(nullable = true, example = "2.5", pattern = QUANTITY_PATTERN)
            String quantity,

            @Nullable @Schema(nullable = true) Boolean checked) {}

    /** The new order: exactly the ids of the items on the list. */
    record ListOrderRequest(@NotNull List<UUID> ids) {}

    /** How many ticked items were taken off the list. */
    record ClearedView(int removed) {}

    /**
     * The list at two or three markets, in the order asked. {@code suggested} is the refs of the two markets with the
     * most priced items, empty when fewer than two have a price. Without markets, {@code markets} is empty and no item
     * has prices. {@code total} is decimal text with two places, over the common items only.
     */
    record CompareView(
            List<String> suggested,
            List<CompareMarketView> markets,
            int commonCount,
            int unitMismatchCount,
            List<CompareItemView> items) {}

    /** A chosen market with its total over the common items and how many list items it has no price for. */
    record CompareMarketView(
            String ref,
            @Nullable @Schema(nullable = true) String name,
            @Schema(example = "24.98") String total,
            int missingCount) {}

    /**
     * A list item; {@code prices} has one entry for each chosen market, in the order of {@code markets}, and null where
     * the market has no price. {@code common}: priced at every market in one unit. {@code unitMismatch}: priced at
     * every market, in different units, so left out of the totals.
     */
    record CompareItemView(
            UUID productId,
            String name,

            @Schema(example = "2.5", pattern = QUANTITY_PATTERN)
            String quantity,

            List<@Nullable ComparePriceView> prices,

            boolean common,
            boolean unitMismatch) {}

    /** The last price at a market; {@code old} when it is from more than two months before today. */
    record ComparePriceView(
            String ref,
            @Schema(example = "4.9900") String unitPrice,
            ProductUnit unit,
            LocalDate observedOn,
            boolean old) {}
}

package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.MarketKind;
import br.com.liberdadefamilia.markets.domain.ProductUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A receipt to save: the key as the lookup took it (the API checks it again), the date, the market (one of the
 * family's, or a new one) and the items. Everything but the key is checked by the service, so each rule has its own
 * code. The text form never shows the key.
 */
record SaveReceiptRequest(
        @NotNull @Schema(description = "The 44 positions; spaces and dots are ignored")
        String chave,

        @Schema(allowableValues = {"0", "1", "2", "3"}) int version,
        @Nullable @Schema(nullable = true) String p,

        @Nullable @Schema(nullable = true, example = "2026-11-10")
        LocalDate issuedOn,

        @Nullable MarketChoice market,
        @Nullable NewMarketChoice newMarket,
        @Nullable List<Item> items) {

    /** A market the family has or that is in OpenStreetMap: {@code osm-<type>-<id>} or the id of its row. */
    record MarketChoice(@Nullable String ref) {}

    /** A market of the family's own, made by the receipt. */
    record NewMarketChoice(@Nullable String name, @Nullable MarketKind kind) {}

    /**
     * One line: a product of the family ({@code productId}) or a new name, in one {@code unit}. Quantity and price are
     * decimal text with up to 4 places.
     */
    record Item(
            @Nullable @Schema(nullable = true) UUID productId,
            @Nullable @Schema(nullable = true) String newProductName,
            @Nullable ProductUnit unit,

            @Nullable @Schema(nullable = true, example = "1.5")
            String quantity,

            @Nullable @Schema(example = "4.99") String unitPrice) {

        @Override
        public String toString() {
            return "Item[***]";
        }
    }

    @Override
    public String toString() {
        return "SaveReceiptRequest[***]";
    }
}

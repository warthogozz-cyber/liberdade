package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.MarketKind;
import br.com.liberdadefamilia.markets.internal.MarketViews.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What the receipt routes answer. */
final class ReceiptViews {

    private ReceiptViews() {}

    /** How the items came: the 10.2 only has {@code MANUAL} (the person types them). */
    enum LookupMode {
        MANUAL
    }

    /** A line read from the receipt. The 10.2 never reads one, so {@code items} is always empty. */
    record LookupItem() {}

    /** The market that has the CNPJ of the key. Archived counts too, since the CNPJ is its own. */
    record LookupMarket(
            String ref,
            UUID familyId,
            @Nullable @Schema(nullable = true) String name,
            @Nullable @Schema(nullable = true) MarketKind kind,
            Status status) {}

    /** What the key says: the state, the month and the market it points to. Nothing is saved. */
    record LookupView(
            LookupMode mode,
            String uf,
            @Schema(example = "2026-11") String issuedMonth,
            @Nullable @Schema(nullable = true) LookupMarket market,
            List<LookupItem> items) {}
}

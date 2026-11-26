package br.com.liberdadefamilia.markets.domain;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Which product a receipt line is: by a valid GTIN first, then by its description at that market, else a new one. */
public final class ProductMatcher {

    public sealed interface Match permits Existing, New {}

    public record Existing(UUID productId) implements Match {}

    public record New() implements Match {}

    private ProductMatcher() {}

    public static Match match(@Nullable String gtin, String descriptionNorm, ProductLookup lookup) {
        if (gtin != null && Gtin.isValid(gtin)) {
            Optional<UUID> byGtin = lookup.byGtin(gtin);
            if (byGtin.isPresent()) {
                return new Existing(byGtin.get());
            }
        }
        return lookup.byAlias(descriptionNorm).<Match>map(Existing::new).orElseGet(New::new);
    }
}

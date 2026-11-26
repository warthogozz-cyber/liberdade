package br.com.liberdadefamilia.markets.domain;

import java.util.Optional;
import java.util.UUID;

/** The two queries the matcher needs, on the family and on the market of the receipt. */
public interface ProductLookup {

    Optional<UUID> byGtin(String gtin);

    Optional<UUID> byAlias(String descriptionNorm);
}

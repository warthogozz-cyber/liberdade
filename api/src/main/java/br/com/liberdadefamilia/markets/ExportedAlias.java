package br.com.liberdadefamilia.markets;

import java.util.UUID;

/** "This description, at this market, is this product." {@code marketId} is the id in the markets export. */
public record ExportedAlias(UUID marketId, String descriptionNorm, UUID productId) {}

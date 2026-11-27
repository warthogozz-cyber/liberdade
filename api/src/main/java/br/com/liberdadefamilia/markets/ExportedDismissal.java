package br.com.liberdadefamilia.markets;

import java.util.UUID;

/** "These two are not the same product"; {@code productA} is the smaller of the two ids. */
public record ExportedDismissal(UUID productA, UUID productB) {}

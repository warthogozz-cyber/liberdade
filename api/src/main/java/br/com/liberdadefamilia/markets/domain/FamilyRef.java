package br.com.liberdadefamilia.markets.domain;

import java.util.UUID;

/** The id of a row in the family's own markets. */
public record FamilyRef(UUID id) implements MarketRef {}

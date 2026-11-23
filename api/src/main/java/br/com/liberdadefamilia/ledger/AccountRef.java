package br.com.liberdadefamilia.ledger;

import java.util.UUID;

/** An account of the family, as the planning module needs to know it. {@code currency} is the ISO 4217 code. */
public record AccountRef(UUID id, String currency, boolean archived) {}

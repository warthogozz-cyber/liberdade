package br.com.liberdadefamilia.ledger;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A category of the family, as the planning module needs to know it. */
public record CategoryRef(UUID id, @Nullable UUID parentId, TransactionKind kind, boolean archived) {}

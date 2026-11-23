package br.com.liberdadefamilia.ledger.internal;

import jakarta.validation.constraints.NotNull;

/** Skips a planned transaction: {@code version} is the one the screen was opened with. */
record SkipTransactionRequest(@NotNull Long version) {}

package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.TransactionKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One description to find a category for, already reduced to its key by the device. */
record CategoryHintItem(
        @NotNull TransactionKind kind,
        @NotNull @Size(max = 140) String key) {}

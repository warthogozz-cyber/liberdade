package br.com.liberdadefamilia.ledger.domain;

import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.shared.Money;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Money that moved on one day: the sum of the transactions of one kind between the same accounts. */
public record Flow(
        LocalDate day,
        TransactionKind kind,
        UUID accountId,
        @Nullable UUID toAccountId,
        Money amount) {}

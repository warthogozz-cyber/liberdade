package br.com.liberdadefamilia.planning;

import br.com.liberdadefamilia.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A goal contribution as the data export carries it, with the name of its goal. A negative amount is a withdrawal. */
public record ExportedContribution(
        UUID id,
        UUID goalId,
        String goalName,
        Money amount,
        LocalDate occurredOn,
        @Nullable UUID memberId,
        Instant createdAt) {}

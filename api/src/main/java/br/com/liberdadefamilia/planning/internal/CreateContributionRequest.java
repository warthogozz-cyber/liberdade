package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.Money;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** A deposit into a goal, or a withdrawal with a negative amount. Zero is not a contribution. */
record CreateContributionRequest(
        @NotNull Money amount, @NotNull LocalDate occurredOn) {}

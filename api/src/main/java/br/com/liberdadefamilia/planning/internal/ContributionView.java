package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A deposit into a goal, or a withdrawal when negative. {@code memberId} is who made it; null once they left. */
record ContributionView(
        UUID id,
        Money amount,
        LocalDate occurredOn,
        @Nullable @Schema(nullable = true) UUID memberId) {}

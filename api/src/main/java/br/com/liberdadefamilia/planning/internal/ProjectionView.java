package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The balance projection (D13): four points, at the end of the current month and of the next three. Without enough
 * history {@code hasHistory} is false, {@code averageSpending} is null and no spending is estimated.
 */
record ProjectionView(
        LocalDate today,
        Money balanceToday,
        boolean hasHistory,
        @Nullable @Schema(nullable = true) Money averageSpending,
        List<ProjectionPointView> points) {}

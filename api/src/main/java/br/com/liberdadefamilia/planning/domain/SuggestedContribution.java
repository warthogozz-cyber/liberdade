package br.com.liberdadefamilia.planning.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** The monthly deposit that reaches a goal by its due month. */
public final class SuggestedContribution {

    private SuggestedContribution() {}

    /**
     * {@code (target - saved) / max(1, months until due)}, rounded up to the cent. Empty without a due month or when
     * the goal is already reached.
     */
    public static Optional<BigDecimal> of(
            BigDecimal target, BigDecimal saved, @Nullable YearMonth due, YearMonth current) {
        if (due == null || saved.compareTo(target) >= 0) {
            return Optional.empty();
        }
        long months = Math.max(1, ChronoUnit.MONTHS.between(current, due));
        return Optional.of(target.subtract(saved).divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING));
    }
}

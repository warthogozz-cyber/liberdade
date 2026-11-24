package br.com.liberdadefamilia.indicators.internal;

import br.com.liberdadefamilia.indicators.domain.IndicatorUnit;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** One indicator series with the health of its collection. */
public record IndicatorSeries(
        IndicatorCode code,
        String source,
        String sourceRef,
        IndicatorUnit unit,
        String name,
        @Nullable Instant lastSuccessAt,
        @Nullable Instant lastFailureAt,
        int failureStreak) {}

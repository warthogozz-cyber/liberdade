package br.com.liberdadefamilia.indicators.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The shapes of the indicators route. Numbers travel as decimal text; the unit and basis are the enum names. */
record IndicatorsView(List<IndicatorView> indicators) {}

/** One card: the last value of a series, how it moved, where it came from and whether it is out of date. */
record IndicatorView(
        String code,
        String name,
        String unit,
        @Nullable @Schema(nullable = true) String value,
        @Nullable @Schema(nullable = true) LocalDate referenceDate,
        @Nullable VariationView variation,
        SourceView source,
        @Nullable @Schema(nullable = true) Instant updatedAt,
        boolean stale) {}

/** How the value moved, already rounded to two decimals; {@code since} is the date it counts from. */
record VariationView(String value, String unit, String basis, LocalDate since) {}

/** Where the number comes from; the URL is that of the newest point and is null when there is none. */
record SourceView(
        String name, @Nullable @Schema(nullable = true) String url) {}

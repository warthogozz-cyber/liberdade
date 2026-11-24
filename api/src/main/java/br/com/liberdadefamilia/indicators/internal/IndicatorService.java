package br.com.liberdadefamilia.indicators.internal;

import br.com.liberdadefamilia.indicators.domain.Point;
import br.com.liberdadefamilia.indicators.domain.Staleness;
import br.com.liberdadefamilia.indicators.domain.Variation;
import br.com.liberdadefamilia.indicators.domain.VariationUnit;
import br.com.liberdadefamilia.indicators.domain.Variations;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Builds the cards: last value, variation, source and staleness of each series. */
@Service
class IndicatorService {

    private static final String SOURCE_NAME = "Banco Central do Brasil";
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    /** Enough daily points to find the last change of the Selic target. */
    private static final int SELIC_POINTS = 400;

    private final IndicatorRepository repository;
    private final Clock clock;

    IndicatorService(IndicatorRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    IndicatorsView list() {
        Instant now = clock.instant();
        List<IndicatorView> cards =
                repository.allSeries().stream().map(series -> card(series, now)).toList();
        return new IndicatorsView(cards);
    }

    private IndicatorView card(IndicatorSeries series, Instant now) {
        IndicatorCode code = series.code();
        int limit = code == IndicatorCode.SELIC_TARGET ? SELIC_POINTS : 2;
        List<Point> points = repository.recentPoints(code, limit);
        Point last = points.isEmpty() ? null : points.getLast();
        return new IndicatorView(
                code.name(),
                series.name(),
                series.unit().name(),
                last == null
                        ? null
                        : last.value()
                                .setScale(code.decimals(), RoundingMode.HALF_UP)
                                .toPlainString(),
                last == null ? null : last.refDate(),
                variation(code, points),
                new SourceView(
                        SOURCE_NAME,
                        last == null ? null : repository.latestSourceUrl(code).orElse(null)),
                series.lastSuccessAt(),
                Staleness.isStale(series.lastSuccessAt(), now, ZONE));
    }

    private static @Nullable VariationView variation(IndicatorCode code, List<Point> points) {
        Variation variation = switch (code.variationRule()) {
            case LAST_CHANGE_PP -> Variations.lastChange(points);
            case PREVIOUS_PP -> Variations.vsPrevious(points, VariationUnit.PP);
            case PREVIOUS_PERCENT -> Variations.vsPrevious(points, VariationUnit.PERCENT);
        };
        if (variation == null) {
            return null;
        }
        return new VariationView(
                variation.value().toPlainString(),
                variation.unit().name(),
                variation.basis().name(),
                variation.since());
    }
}

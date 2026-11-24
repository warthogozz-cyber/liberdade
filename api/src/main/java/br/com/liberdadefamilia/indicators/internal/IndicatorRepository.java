package br.com.liberdadefamilia.indicators.internal;

import br.com.liberdadefamilia.indicators.domain.IndicatorUnit;
import br.com.liberdadefamilia.indicators.domain.Point;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The indicator series, their points and the health of their collection. Public data: no family id anywhere. */
@Repository
public class IndicatorRepository {

    private final JdbcClient jdbc;

    IndicatorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Every series, in the order of the cards. */
    public List<IndicatorSeries> allSeries() {
        return jdbc
                .sql("""
                        select code, source, source_ref, unit, name, last_success_at, last_failure_at, failure_streak
                        from indicator_series""")
                .query((rs, row) -> new IndicatorSeries(
                        IndicatorCode.valueOf(rs.getString("code")),
                        rs.getString("source"),
                        rs.getString("source_ref"),
                        IndicatorUnit.valueOf(rs.getString("unit")),
                        rs.getString("name"),
                        instant(rs.getTimestamp("last_success_at")),
                        instant(rs.getTimestamp("last_failure_at")),
                        rs.getInt("failure_streak")))
                .list()
                .stream()
                .sorted(Comparator.comparing(IndicatorSeries::code))
                .toList();
    }

    public Optional<LocalDate> lastRefDate(IndicatorCode code) {
        return jdbc.sql("select max(ref_date) from indicator_points where series_code = :code")
                .param("code", code.name())
                .query((rs, row) -> rs.getObject(1, LocalDate.class))
                .optional();
    }

    /** Writes the points; a point of a day already stored replaces it. */
    public void upsertPoints(IndicatorCode code, List<FetchedPoint> points, Instant fetchedAt) {
        for (FetchedPoint point : points) {
            jdbc.sql("""
                            insert into indicator_points (series_code, ref_date, value, fetched_at, source_url)
                            values (:code, :refDate, :value, :fetchedAt, :sourceUrl)
                            on conflict (series_code, ref_date) do update
                            set value = excluded.value, fetched_at = excluded.fetched_at,
                                source_url = excluded.source_url""")
                    .param("code", code.name())
                    .param("refDate", point.refDate())
                    .param("value", point.value())
                    .param("fetchedAt", Timestamp.from(fetchedAt))
                    .param("sourceUrl", point.sourceUrl())
                    .update();
        }
    }

    public void markSuccess(IndicatorCode code, Instant at) {
        jdbc.sql("update indicator_series set last_success_at = :at, failure_streak = 0 where code = :code")
                .param("at", Timestamp.from(at))
                .param("code", code.name())
                .update();
    }

    public void markFailure(IndicatorCode code, Instant at) {
        jdbc.sql("""
                        update indicator_series
                        set last_failure_at = :at, failure_streak = failure_streak + 1
                        where code = :code""").param("at", Timestamp.from(at)).param("code", code.name()).update();
    }

    /** The {@code limit} newest points, oldest first. */
    public List<Point> recentPoints(IndicatorCode code, int limit) {
        List<Point> newestFirst = jdbc.sql("""
                        select ref_date, value from indicator_points
                        where series_code = :code order by ref_date desc limit :limit""")
                .param("code", code.name())
                .param("limit", limit)
                .query((rs, row) -> new Point(rs.getObject("ref_date", LocalDate.class), rs.getBigDecimal("value")))
                .list();
        return newestFirst.reversed();
    }

    /** The public URL of the newest point. */
    public Optional<String> latestSourceUrl(IndicatorCode code) {
        return jdbc.sql("""
                        select source_url from indicator_points
                        where series_code = :code order by ref_date desc limit 1""").param("code", code.name()).query(String.class).optional();
    }

    private static @Nullable Instant instant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

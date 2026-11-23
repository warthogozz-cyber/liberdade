package br.com.liberdadefamilia.planning.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Property tests with a fixed seed (see ADR-0013): 1000 cases per property. */
class RecurrenceScheduleProperties {

    private static final long SEED = 20261102L;
    private static final int CASES = 1000;
    private static final Frequency[] FREQUENCIES = Frequency.values();

    @ParameterizedTest
    @MethodSource("rules")
    void occurrencesNeverDrift(
            Frequency frequency,
            int interval,
            LocalDate startOn,
            @Nullable LocalDate endOn,
            LocalDate from,
            LocalDate to) {
        List<LocalDate> dates = RecurrenceSchedule.occurrences(frequency, interval, startOn, endOn, from, to);

        LocalDate lower = from.isAfter(startOn) ? from : startOn;
        LocalDate upper = endOn != null && endOn.isBefore(to) ? endOn : to;
        List<LocalDate> expected = LongStream.rangeClosed(0, 4000)
                .mapToObj(k -> unit(frequency, startOn, k * interval))
                .filter(date -> !date.isBefore(lower) && !date.isAfter(upper))
                .toList();

        assertThat(dates).doesNotHaveDuplicates().isSorted().containsExactlyElementsOf(expected);
        for (LocalDate date : dates) {
            assertThat(date).isBetween(lower, upper);
            if (frequency == Frequency.MONTHLY) {
                assertThat(date.getDayOfMonth())
                        .isEqualTo(Math.min(
                                startOn.getDayOfMonth(), YearMonth.from(date).lengthOfMonth()));
            }
        }
    }

    private static LocalDate unit(Frequency frequency, LocalDate startOn, long units) {
        return switch (frequency) {
            case WEEKLY -> startOn.plusWeeks(units);
            case MONTHLY -> startOn.plusMonths(units);
            case YEARLY -> startOn.plusYears(units);
        };
    }

    static Stream<Arguments> rules() {
        var random = new SplittableRandom(SEED);
        LocalDate base = LocalDate.of(2024, 1, 1);
        return Stream.generate(() -> {
                    LocalDate start = base.plusDays(random.nextLong(0, 7 * 366));
                    LocalDate from = start.plusDays(random.nextLong(-100, 2000));
                    LocalDate to = from.plusDays(random.nextLong(0, 401));
                    LocalDate end = random.nextBoolean() ? null : start.plusDays(random.nextLong(0, 2500));
                    return Arguments.of(
                            FREQUENCIES[random.nextInt(FREQUENCIES.length)],
                            random.nextInt(1, 13),
                            start,
                            end,
                            from,
                            to);
                })
                .limit(CASES);
    }
}

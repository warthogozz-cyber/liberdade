package br.com.liberdadefamilia.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.shared.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Property tests with a fixed seed (see ADR-0013): 1000 cases per property. */
class BalanceCalculatorPropertiesTest {

    private static final long SEED = 20261102L;
    private static final int CASES = 1000;
    private static final Currency BRL = Currency.getInstance("BRL");
    private static final YearMonth MONTH = YearMonth.of(2026, 11);

    /** One to six accounts, some of them active, and up to 40 flows from 20 days before the month to its last day. */
    record Scenario(Map<UUID, Money> active, List<Flow> flows) {}

    @ParameterizedTest
    @MethodSource("scenarios")
    void transfersBetweenActiveAccountsMoveNothing(Scenario scenario) {
        var withoutInternalTransfers = scenario.flows().stream()
                .filter(flow -> !isInternalTransfer(flow, scenario.active()))
                .toList();

        var all = BalanceCalculator.summarize(MONTH, BRL, scenario.active(), scenario.flows());
        var without = BalanceCalculator.summarize(MONTH, BRL, scenario.active(), withoutInternalTransfers);

        assertThat(all.balance()).isEqualTo(without.balance());
        assertThat(all.daily()).isEqualTo(without.daily());
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void lastDayIsTheBalance(Scenario scenario) {
        var summary = BalanceCalculator.summarize(MONTH, BRL, scenario.active(), scenario.flows());

        assertThat(summary.daily().getLast().balance()).isEqualTo(summary.balance());
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void resultIsIncomeMinusExpenses(Scenario scenario) {
        var summary = BalanceCalculator.summarize(MONTH, BRL, scenario.active(), scenario.flows());

        assertThat(summary.result()).isEqualTo(summary.income().minus(summary.expenses()));
    }

    @ParameterizedTest
    @MethodSource("scenarios")
    void oneDailyPointPerDay(Scenario scenario) {
        var summary = BalanceCalculator.summarize(MONTH, BRL, scenario.active(), scenario.flows());

        assertThat(summary.daily()).hasSize(MONTH.lengthOfMonth());
    }

    private static boolean isInternalTransfer(Flow flow, Map<UUID, Money> active) {
        return flow.kind() == TransactionKind.TRANSFER
                && active.containsKey(flow.accountId())
                && active.containsKey(flow.toAccountId());
    }

    static Stream<Arguments> scenarios() {
        var random = new SplittableRandom(SEED);
        return Stream.generate(() -> Arguments.of(scenario(random))).limit(CASES);
    }

    private static Scenario scenario(SplittableRandom random) {
        int count = random.nextInt(1, 7);
        List<UUID> accounts = new ArrayList<>();
        Map<UUID, Money> active = new HashMap<>();
        for (int i = 0; i < count; i++) {
            UUID id = new UUID(random.nextLong(), random.nextLong());
            accounts.add(id);
            if (i == 0 || random.nextBoolean()) {
                active.put(id, money(random));
            }
        }
        int flowCount = random.nextInt(0, 41);
        List<Flow> flows = new ArrayList<>();
        for (int i = 0; i < flowCount; i++) {
            LocalDate day = MONTH.atDay(1).plusDays(random.nextInt(-20, MONTH.lengthOfMonth()));
            UUID from = accounts.get(random.nextInt(count));
            // A transfer needs a second account.
            var kind = TransactionKind.values()[random.nextInt(count > 1 ? 3 : 2)];
            UUID to = null;
            if (kind == TransactionKind.TRANSFER) {
                do {
                    to = accounts.get(random.nextInt(count));
                } while (to.equals(from));
            }
            flows.add(new Flow(day, kind, from, to, money(random)));
        }
        return new Scenario(Map.copyOf(active), List.copyOf(flows));
    }

    private static Money money(SplittableRandom random) {
        return Money.of(BigDecimal.valueOf(random.nextLong(1, 10_000_000), 2), BRL);
    }
}

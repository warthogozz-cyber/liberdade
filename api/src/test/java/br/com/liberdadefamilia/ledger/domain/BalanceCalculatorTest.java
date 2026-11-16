package br.com.liberdadefamilia.ledger.domain;

import static br.com.liberdadefamilia.ledger.domain.TransactionKind.EXPENSE;
import static br.com.liberdadefamilia.ledger.domain.TransactionKind.INCOME;
import static br.com.liberdadefamilia.ledger.domain.TransactionKind.TRANSFER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.shared.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BalanceCalculatorTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final Currency BRL = Currency.getInstance("BRL");
    private static final UUID CHECKING = new UUID(0, 1);
    private static final UUID SAVINGS = new UUID(0, 2);
    private static final UUID WALLET = new UUID(0, 3);
    private static final UUID ARCHIVED = new UUID(0, 4);
    private static final UUID ARCHIVED_TOO = new UUID(0, 5);

    @Test
    void prdExample() {
        var active = Map.of(CHECKING, Money.brl("1000"), SAVINGS, Money.brl("0"), WALLET, Money.brl("0"));
        var day5 = LocalDate.of(2026, 11, 5);
        var flows = List.of(
                flow(day5, INCOME, CHECKING, null, "5000"),
                flow(day5, EXPENSE, CHECKING, null, "1234.56"),
                flow(day5, TRANSFER, CHECKING, SAVINGS, "500"));

        var summary = BalanceCalculator.summarize(NOVEMBER, BRL, active, flows);

        assertThat(summary.income()).isEqualTo(Money.brl("5000.00"));
        assertThat(summary.expenses()).isEqualTo(Money.brl("1234.56"));
        assertThat(summary.result()).isEqualTo(Money.brl("3765.44"));
        assertThat(summary.balance()).isEqualTo(Money.brl("4765.44"));
        assertThat(summary.daily().get(3)).isEqualTo(new DailyBalance(LocalDate.of(2026, 11, 4), Money.brl("1000.00")));
        for (int day = 5; day <= 30; day++) {
            assertThat(summary.daily().get(day - 1).balance()).as("day %d", day).isEqualTo(Money.brl("4765.44"));
        }
    }

    @Test
    void openingBalanceIncludesPastFlows() {
        var active = Map.of(CHECKING, Money.brl("1000"));
        var flows = List.of(flow(LocalDate.of(2026, 10, 20), EXPENSE, CHECKING, null, "100"));

        var summary = BalanceCalculator.summarize(NOVEMBER, BRL, active, flows);

        assertThat(summary.balance()).isEqualTo(Money.brl("900.00"));
        assertThat(summary.expenses()).isEqualTo(Money.brl("0.00"));
        assertThat(summary.daily().get(0).balance()).isEqualTo(Money.brl("900.00"));
    }

    @Test
    void transferFromArchivedToActive() {
        var active = Map.of(CHECKING, Money.brl("10"));
        var flows = List.of(flow(LocalDate.of(2026, 11, 3), TRANSFER, ARCHIVED, CHECKING, "300"));

        var summary = BalanceCalculator.summarize(NOVEMBER, BRL, active, flows);

        assertThat(summary.balance()).isEqualTo(Money.brl("310.00"));
    }

    @Test
    void transferBetweenArchivedAccounts() {
        var active = Map.of(CHECKING, Money.brl("10"));
        var flows = List.of(flow(LocalDate.of(2026, 11, 3), TRANSFER, ARCHIVED, ARCHIVED_TOO, "300"));

        var summary = BalanceCalculator.summarize(NOVEMBER, BRL, active, flows);

        assertThat(summary.balance()).isEqualTo(Money.brl("10.00"));
    }

    @Test
    void transferFromActiveToArchived() {
        var active = Map.of(CHECKING, Money.brl("500"));
        var flows = List.of(flow(LocalDate.of(2026, 11, 3), TRANSFER, CHECKING, ARCHIVED, "300"));

        var summary = BalanceCalculator.summarize(NOVEMBER, BRL, active, flows);

        assertThat(summary.balance()).isEqualTo(Money.brl("200.00"));
    }

    @Test
    void archivedAccountStillCountsInFlows() {
        var active = Map.of(CHECKING, Money.brl("10"));
        var flows = List.of(
                flow(LocalDate.of(2026, 11, 3), EXPENSE, ARCHIVED, null, "50"),
                flow(LocalDate.of(2026, 11, 4), INCOME, ARCHIVED, null, "20"));

        var summary = BalanceCalculator.summarize(NOVEMBER, BRL, active, flows);

        assertThat(summary.expenses()).isEqualTo(Money.brl("50.00"));
        assertThat(summary.income()).isEqualTo(Money.brl("20.00"));
        assertThat(summary.balance()).isEqualTo(Money.brl("10.00"));
    }

    @Test
    void transferFromOutsideScopeCountsInBalanceOnly() {
        // The youth's dashboard: only the wallet is in the map, and the allowance comes from a family account.
        var scoped = Map.of(WALLET, Money.brl("0"));
        var flows = List.of(
                flow(LocalDate.of(2026, 11, 3), TRANSFER, CHECKING, WALLET, "50"),
                flow(LocalDate.of(2026, 11, 4), EXPENSE, WALLET, null, "10"));

        var summary = BalanceCalculator.summarize(NOVEMBER, BRL, scoped, flows);

        assertThat(summary.income()).isEqualTo(Money.brl("0.00"));
        assertThat(summary.expenses()).isEqualTo(Money.brl("10.00"));
        assertThat(summary.balance()).isEqualTo(Money.brl("40.00"));
        assertThat(summary.daily().get(1).balance()).isEqualTo(Money.brl("0.00"));
        assertThat(summary.daily().get(2).balance()).isEqualTo(Money.brl("50.00"));
        assertThat(summary.daily().get(3).balance()).isEqualTo(Money.brl("40.00"));

        // Between two accounts of the map, a transfer moves money without changing the total.
        var both = Map.of(WALLET, Money.brl("0"), CHECKING, Money.brl("100"));
        var inside = BalanceCalculator.summarize(
                NOVEMBER, BRL, both, List.of(flow(LocalDate.of(2026, 11, 3), TRANSFER, CHECKING, WALLET, "50")));

        assertThat(inside.balance()).isEqualTo(Money.brl("100.00"));
        assertThat(inside.income()).isEqualTo(Money.brl("0.00"));
        assertThat(inside.expenses()).isEqualTo(Money.brl("0.00"));
    }

    @Test
    void dailyCoversWholeMonth() {
        var summary = BalanceCalculator.summarize(YearMonth.of(2028, 2), BRL, Map.of(), List.of());

        assertThat(summary.daily()).hasSize(29);
        assertThat(summary.daily().getFirst().day()).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(summary.daily().getLast().day()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void rejectsFlowAfterMonth() {
        var flows = List.of(flow(LocalDate.of(2026, 12, 1), EXPENSE, CHECKING, null, "1"));

        assertThatThrownBy(() -> BalanceCalculator.summarize(NOVEMBER, BRL, Map.of(), flows))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFlowInAnotherCurrency() {
        var usd = new Flow(
                LocalDate.of(2026, 11, 1), EXPENSE, CHECKING, null, Money.parse("7", Currency.getInstance("USD")));

        assertThatThrownBy(() -> BalanceCalculator.summarize(NOVEMBER, BRL, Map.of(), List.of(usd)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("7");
    }

    private static Flow flow(LocalDate day, TransactionKind kind, UUID account, UUID to, String amount) {
        return new Flow(day, kind, account, to, Money.brl(amount));
    }
}

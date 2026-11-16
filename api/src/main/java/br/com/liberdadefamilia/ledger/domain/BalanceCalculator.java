package br.com.liberdadefamilia.ledger.domain;

import br.com.liberdadefamilia.shared.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sums a month of the family's flows. Income and expenses count every account, archived ones included; the balance
 * counts only the active accounts (the ones in the map), so a transfer between two of them moves nothing.
 */
public final class BalanceCalculator {

    private BalanceCalculator() {}

    /**
     * @param activeInitialBalances the initial balance of each active account
     * @param flows what happened up to the end of the month; flows before the month only move the opening balance
     * @throws IllegalArgumentException for a flow after the month or in another currency
     */
    public static MonthSummary summarize(
            YearMonth month, Currency currency, Map<UUID, Money> activeInitialBalances, List<Flow> flows) {
        LocalDate first = month.atDay(1);
        Money opening = Money.zero(currency);
        for (Money initial : activeInitialBalances.values()) {
            opening = opening.plus(initial);
        }
        Money income = Money.zero(currency);
        Money expenses = Money.zero(currency);
        Money[] movement = new Money[month.lengthOfMonth()];
        Arrays.fill(movement, Money.zero(currency));
        for (Flow flow : flows) {
            if (!flow.amount().currency().equals(currency)) {
                throw new IllegalArgumentException("flow is in another currency");
            }
            if (flow.day().isAfter(month.atEndOfMonth())) {
                throw new IllegalArgumentException("flow is after the month");
            }
            Money effect = effectOnBalance(flow, activeInitialBalances);
            if (flow.day().isBefore(first)) {
                opening = opening.plus(effect);
                continue;
            }
            int index = flow.day().getDayOfMonth() - 1;
            movement[index] = movement[index].plus(effect);
            switch (flow.kind()) {
                case INCOME -> income = income.plus(flow.amount());
                case EXPENSE -> expenses = expenses.plus(flow.amount());
                case TRANSFER -> {}
            }
        }
        List<DailyBalance> daily = new ArrayList<>(movement.length);
        Money running = opening;
        for (int i = 0; i < movement.length; i++) {
            running = running.plus(movement[i]);
            daily.add(new DailyBalance(month.atDay(i + 1), running));
        }
        return new MonthSummary(income, expenses, income.minus(expenses), running, List.copyOf(daily));
    }

    private static Money effectOnBalance(Flow flow, Map<UUID, Money> active) {
        Money zero = Money.zero(flow.amount().currency());
        boolean fromActive = active.containsKey(flow.accountId());
        return switch (flow.kind()) {
            case INCOME -> fromActive ? flow.amount() : zero;
            case EXPENSE -> fromActive ? flow.amount().negate() : zero;
            case TRANSFER -> {
                Money effect = fromActive ? flow.amount().negate() : zero;
                yield flow.toAccountId() != null && active.containsKey(flow.toAccountId())
                        ? effect.plus(flow.amount())
                        : effect;
            }
        };
    }
}

package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import java.util.List;

/**
 * The month at a glance, in the family's base currency.
 *
 * @param month the month asked for, as {@code yyyy-MM}
 * @param balance the balance of the active accounts at the end of the month
 * @param byCategory the biggest spending categories, this month and last, and "Outras" for the rest
 * @param dailyBalance the balance at the end of each day of the month
 */
record DashboardView(
        String month,
        Money income,
        Money expenses,
        Money result,
        Money balance,
        List<DashboardCategory> byCategory,
        List<DashboardDay> dailyBalance) {}

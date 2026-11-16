package br.com.liberdadefamilia.ledger.domain;

import br.com.liberdadefamilia.shared.Money;
import java.util.List;

/**
 * A month of the family's money: what came in, what went out, the difference, the balance at the end of the month, and
 * the balance at the end of each day.
 */
public record MonthSummary(Money income, Money expenses, Money result, Money balance, List<DailyBalance> daily) {}

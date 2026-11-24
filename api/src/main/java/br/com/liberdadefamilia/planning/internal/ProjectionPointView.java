package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.shared.Money;
import java.time.LocalDate;

/**
 * The projected balance at a month end: the balance today plus {@code scheduled} less {@code estimatedSpending}, both
 * accumulated from today up to that day.
 */
record ProjectionPointView(LocalDate monthEnd, Money balance, Money scheduled, Money estimatedSpending) {}

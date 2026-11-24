package br.com.liberdadefamilia.planning.internal;

import java.time.YearMonth;
import java.util.List;

/** The month's limits, in the order of the category names. */
record BudgetMonthView(YearMonth month, List<BudgetLineView> lines) {}

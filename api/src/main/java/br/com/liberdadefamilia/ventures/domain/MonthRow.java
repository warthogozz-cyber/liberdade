package br.com.liberdadefamilia.ventures.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import org.jspecify.annotations.Nullable;

/** One month of a project: the real figures and, inside the planned period, the planned ones. */
public record MonthRow(
        YearMonth month,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal result,
        @Nullable BigDecimal plannedIncome,
        @Nullable BigDecimal plannedCost,
        @Nullable BigDecimal plannedResult,
        @Nullable Comparison comparison) {}

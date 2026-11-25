package br.com.liberdadefamilia.ventures.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** The break-even point; the numbers are present only when the state is OK. */
public record BreakEven(
        BreakEvenState state,
        @Nullable BigDecimal revenue,
        @Nullable BigDecimal weightedMarginPercent) {}

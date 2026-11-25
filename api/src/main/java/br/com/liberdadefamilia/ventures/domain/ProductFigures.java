package br.com.liberdadefamilia.ventures.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record ProductFigures(
        BigDecimal margin,
        BigDecimal marginPercent,
        boolean priceCoversCost,
        @Nullable Long breakEvenUnits) {}

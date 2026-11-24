package br.com.liberdadefamilia.planning;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** The emergency reserve settings of the family: the months to cover and the monthly cost typed in, if any. */
public record ExportedReserveSettings(
        int targetMonths, @Nullable BigDecimal manualMonthlyCost) {}

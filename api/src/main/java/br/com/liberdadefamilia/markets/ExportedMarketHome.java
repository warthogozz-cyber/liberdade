package br.com.liberdadefamilia.markets;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** The address of the family's markets as the data export carries it: a point of three decimals and its label. */
public record ExportedMarketHome(
        BigDecimal lat, BigDecimal lon, @Nullable String label) {}

package br.com.liberdadefamilia.ventures.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * The payback measured on what really happened. {@code investment} is the lowest point of the
 * running result; {@code months} is present only when paid back; {@code missing} only when not yet.
 */
public record RealPayback(
        RealPaybackState state,
        @Nullable BigDecimal investment,
        @Nullable Integer months,
        @Nullable BigDecimal missing) {}

package br.com.liberdadefamilia.shared;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An exact monetary amount. The amount fits {@code NUMERIC(19,4)} and is normalized so that
 * {@code equals} and {@code hashCode} ignore trailing zeros.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    private static final int MAX_SCALE = 4;
    private static final BigDecimal LIMIT = BigDecimal.TEN.pow(15);
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("^-?\\d{1,15}(\\.\\d{1,4})?$");
    private static final Currency BRL = Currency.getInstance("BRL");

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) {
            throw new IllegalArgumentException("currency must define fraction digits");
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > MAX_SCALE) {
            throw new IllegalArgumentException("amount must have at most 4 decimal places");
        }
        if (amount.abs().compareTo(LIMIT) >= 0) {
            throw new IllegalArgumentException("amount must be below 10^15");
        }
        amount = amount.setScale(Math.max(stripped.scale(), digits), RoundingMode.UNNECESSARY);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    /** Strict plain decimal: optional minus, up to 15 integer digits, up to 4 decimal places. */
    public static Money parse(String amount, Currency currency) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (!PLAIN_DECIMAL.matcher(amount).matches()) {
            throw new IllegalArgumentException("amount must be a plain decimal with at most 4 decimal places");
        }
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money brl(String amount) {
        return parse(amount, BRL);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    /** Plain text at the currency scale, rounding half-even. */
    public String toPlainString() {
        return amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN)
                .toPlainString();
    }

    /**
     * Splits this amount into {@code parts} parts using the largest-remainder method: the first
     * {@code remainder} parts get one extra minor unit.
     */
    public List<Money> allocate(int parts) {
        if (parts < 1) {
            throw new IllegalArgumentException("parts must be at least 1");
        }
        if (amount.signum() < 0) {
            return negate().allocate(parts).stream().map(Money::negate).toList();
        }
        int digits = currency.getDefaultFractionDigits();
        BigInteger units;
        try {
            units = amount.movePointRight(digits).toBigIntegerExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("amount must not have a fraction of a minor unit", e);
        }
        BigInteger[] divided = units.divideAndRemainder(BigInteger.valueOf(parts));
        int remainder = divided[1].intValueExact();
        List<Money> result = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            BigInteger part = i < remainder ? divided[0].add(BigInteger.ONE) : divided[0];
            result.add(new Money(new BigDecimal(part, digits), currency));
        }
        return List.copyOf(result);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException();
        }
    }
}

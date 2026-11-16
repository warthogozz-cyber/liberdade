package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.shared.Money;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

/** Checks {@link TransactionAmount}. */
final class TransactionAmountValidator implements ConstraintValidator<TransactionAmount, Money> {

    static final BigDecimal MAX = new BigDecimal("999999999.99");

    @Override
    public boolean isValid(Money money, ConstraintValidatorContext context) {
        if (money == null) {
            return true;
        }
        BigDecimal amount = money.amount();
        return amount.signum() > 0
                && amount.compareTo(MAX) <= 0
                && amount.stripTrailingZeros().scale() <= money.currency().getDefaultFractionDigits();
    }
}

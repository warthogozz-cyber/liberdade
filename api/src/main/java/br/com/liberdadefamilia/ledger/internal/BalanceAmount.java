package br.com.liberdadefamilia.ledger.internal;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An account balance: at most 999,999,999.99 in absolute value, with no more decimal places than its currency has.
 * Negative and zero pass. A null value passes; {@code @NotNull} covers it.
 */
@Documented
@Constraint(validatedBy = BalanceAmountValidator.class)
@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
@interface BalanceAmount {

    String message() default "must be at most 999999999.99 in absolute value and fit the currency";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

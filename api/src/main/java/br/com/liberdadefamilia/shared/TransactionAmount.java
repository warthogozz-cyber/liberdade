package br.com.liberdadefamilia.shared;

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
 * A transaction amount: above zero, at most 999,999,999.99, and with no more decimal places than its currency has. A
 * null amount passes; {@code @NotNull} covers it.
 */
@Documented
@Constraint(validatedBy = TransactionAmountValidator.class)
@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
public @interface TransactionAmount {

    String message() default "must be above zero, at most 999999999.99 and fit the currency";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

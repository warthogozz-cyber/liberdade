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
 * The tag names of a transaction: each one non-null, of 1 to 30 characters once stripped and without control
 * characters, and at most 5 distinct names, letter case aside. A null list passes.
 */
@Documented
@Constraint(validatedBy = TagNamesValidator.class)
@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
@interface TagNames {

    String message() default "must hold at most 5 distinct names of 1 to 30 characters, without control characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

package br.com.liberdadefamilia.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the body of a PATCH: every field is optional, and the contract says so with an explicit empty
 * {@code required} list. Without the list, the web generator ({@code --properties-required-by-default}) types every
 * field as required.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PartialRequest {}

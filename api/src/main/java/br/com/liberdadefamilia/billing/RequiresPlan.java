package br.com.liberdadefamilia.billing;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a family route that needs a plan of at least this level. It runs after the family guard, so a caller who may
 * not use the route, or is not in the family, never learns what plan the family is on.
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface RequiresPlan {

    PlanKey value();
}

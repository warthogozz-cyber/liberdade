package br.com.liberdadefamilia.identity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as a sensitive action (US-01): it answers 403 {@code auth.reauthentication-required}
 * unless the person signed in within the last 10 minutes.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresRecentSignIn {}

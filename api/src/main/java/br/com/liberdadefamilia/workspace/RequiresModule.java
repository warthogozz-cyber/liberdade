package br.com.liberdadefamilia.workspace;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a {@link WorkspaceScoped} handler as belonging to an optional module. After the role check, the guard answers
 * 403 {@code module.disabled} when the family has that module off.
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface RequiresModule {

    ModuleKey value();
}

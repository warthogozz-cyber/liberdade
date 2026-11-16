package br.com.liberdadefamilia.workspace;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a handler under {@code /v1/workspaces/{ws}} as reachable by members with one of the given roles. The guard
 * denies every handler there without it. The method gets the caller's {@link WorkspaceMember} by declaring a parameter
 * of that type.
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface WorkspaceScoped {

    Role[] allow();
}

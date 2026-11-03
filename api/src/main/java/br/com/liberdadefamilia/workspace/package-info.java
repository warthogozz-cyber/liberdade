/**
 * Families: creating one on first sign-in, who belongs to it with which {@link Role}, and the guard every route under
 * {@code /v1/workspaces/{ws}} goes through ({@link WorkspaceScoped}).
 */
@ApplicationModule(allowedDependencies = {"identity", "shared"})
package br.com.liberdadefamilia.workspace;

import org.springframework.modulith.ApplicationModule;

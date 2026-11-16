package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.Modules;
import br.com.liberdadefamilia.workspace.RequiresModule;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * The guard on {@code /v1/workspaces/{ws}/**} (ADR-0008). It denies by default, and the first check that fails decides:
 *
 * <ol>
 *   <li>a handler without {@link WorkspaceScoped}: 403;
 *   <li>a {@code {ws}} that is not a UUID: 404;
 *   <li>no ACTIVE membership in that family, or a family that asked for deletion (unless the route sets
 *       {@code includePendingDeletion} and the caller is an Owner): 404, so nobody learns whether it exists;
 *   <li>a role that {@code allow} does not list: 403;
 *   <li>a handler marked {@link RequiresModule} whose module the family has off: 403 {@code module.disabled}. It
 *       comes after the role check, so a role that may not use the route never learns whether the module is on.
 * </ol>
 *
 * <p>Otherwise the {@link WorkspaceMember} goes into a request attribute for {@link WorkspaceMemberArgumentResolver}.
 */
class WorkspaceGuardInterceptor implements HandlerInterceptor {

    static final String MEMBER_ATTRIBUTE = WorkspaceMember.class.getName();

    private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();
    private final WorkspaceRepository workspaces;
    private final Modules modules;
    private final SecurityContextHolderStrategy contexts;

    WorkspaceGuardInterceptor(WorkspaceRepository workspaces, Modules modules, SecurityContextHolderStrategy contexts) {
        this.workspaces = workspaces;
        this.modules = modules;
        this.contexts = contexts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        WorkspaceScoped scope =
                handler instanceof HandlerMethod method ? method.getMethodAnnotation(WorkspaceScoped.class) : null;
        if (scope == null) {
            throw new AccessDeniedException("Family route without @WorkspaceScoped");
        }
        WorkspaceMember member = workspaces
                .activeMember(workspaceIdOf(request), currentUserId(), scope.includePendingDeletion())
                .orElseThrow(WorkspaceNotFoundException::new);
        if (!Arrays.asList(scope.allow()).contains(member.role())) {
            throw new AccessDeniedException("Role not allowed on this route");
        }
        RequiresModule module = ((HandlerMethod) handler).getMethodAnnotation(RequiresModule.class);
        if (module != null && !modules.isEnabled(member.workspaceId(), module.value())) {
            throw new ModuleDisabledException();
        }
        request.setAttribute(MEMBER_ATTRIBUTE, member);
        return true;
    }

    private static UUID workspaceIdOf(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        var variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String ws = variables == null ? null : variables.get("ws");
        if (ws == null) {
            throw new WorkspaceNotFoundException();
        }
        try {
            return UUID.fromString(ws);
        } catch (IllegalArgumentException notAUuid) {
            throw new WorkspaceNotFoundException();
        }
    }

    // Spring Security turns anonymous requests away first; these checks keep the guard safe on its own.
    private UUID currentUserId() {
        Authentication authentication = contexts.getContext().getAuthentication();
        if (authentication == null || !trust.isAuthenticated(authentication)) {
            throw new AuthenticationCredentialsNotFoundException("Not signed in");
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException notAUserId) {
            throw new InsufficientAuthenticationException("Principal name is not a user id", notAUserId);
        }
    }
}

package br.com.liberdadefamilia.workspace;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Where the family guard leaves the {@link WorkspaceMember} it resolved, for code that runs after it (another module's
 * interceptor, for one). Handlers just declare a {@link WorkspaceMember} parameter instead.
 */
public final class WorkspaceRequests {

    /**
     * The order of the family guard in the interceptor registry. A guard that needs the member registers itself with a
     * higher order, e.g. {@code GUARD_ORDER + 100}.
     */
    public static final int GUARD_ORDER = 100;

    private static final String MEMBER_ATTRIBUTE = WorkspaceMember.class.getName();

    private WorkspaceRequests() {}

    /** The member the family guard resolved for this request, or empty when the guard did not run. */
    public static Optional<WorkspaceMember> member(HttpServletRequest request) {
        return request.getAttribute(MEMBER_ATTRIBUTE) instanceof WorkspaceMember member
                ? Optional.of(member)
                : Optional.empty();
    }

    /** Only the family guard calls this. */
    public static void attach(HttpServletRequest request, WorkspaceMember member) {
        request.setAttribute(MEMBER_ATTRIBUTE, member);
    }
}

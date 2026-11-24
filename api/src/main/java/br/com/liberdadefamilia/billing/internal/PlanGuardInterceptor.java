package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.PlanRequiredException;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.billing.RequiresPlan;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Answers 403 {@code plan.required} on a {@link RequiresPlan} route when the family's plan is below it. It runs after
 * the family guard, which leaves the member in the request; a missing member means the order of the interceptors is
 * wrong, and that fails loudly.
 */
class PlanGuardInterceptor implements HandlerInterceptor {

    private final Plans plans;

    PlanGuardInterceptor(Plans plans) {
        this.plans = plans;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        RequiresPlan required =
                handler instanceof HandlerMethod method ? method.getMethodAnnotation(RequiresPlan.class) : null;
        if (required == null) {
            return true;
        }
        WorkspaceMember member = WorkspaceRequests.member(request)
                .orElseThrow(() -> new IllegalStateException("The plan guard ran before the family guard"));
        if (required.openWhileBillingOff() && !plans.legalSetEnabled()) {
            return true;
        }
        if (plans.current(member.workspaceId()).key().compareTo(required.value()) < 0) {
            throw new PlanRequiredException();
        }
        return true;
    }
}

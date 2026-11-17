package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceRequests;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Hands a guarded method the {@link WorkspaceMember} that {@link WorkspaceGuardInterceptor} checked. */
class WorkspaceMemberArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == WorkspaceMember.class;
    }

    @Override
    public WorkspaceMember resolveArgument(
            MethodParameter parameter,
            @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            @Nullable WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return Optional.ofNullable(request)
                .flatMap(WorkspaceRequests::member)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "No WorkspaceMember: the handler must be @WorkspaceScoped and mapped under /v1/workspaces/{ws}"));
    }
}

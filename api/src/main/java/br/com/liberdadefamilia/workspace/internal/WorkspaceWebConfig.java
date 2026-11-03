package br.com.liberdadefamilia.workspace.internal;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Puts the guard on every family route and lets guarded methods take a {@code WorkspaceMember} parameter. */
@Configuration(proxyBeanMethods = false)
class WorkspaceWebConfig implements WebMvcConfigurer {

    private final WorkspaceRepository workspaces;

    WorkspaceWebConfig(WorkspaceRepository workspaces) {
        this.workspaces = workspaces;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // A trailing "/**" also matches no segment at all, so /v1/workspaces/{ws} itself is guarded too.
        registry.addInterceptor(
                        new WorkspaceGuardInterceptor(workspaces, SecurityContextHolder.getContextHolderStrategy()))
                .addPathPatterns("/v1/workspaces/*/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new WorkspaceMemberArgumentResolver());
    }
}

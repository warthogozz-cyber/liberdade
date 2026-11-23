package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.Modules;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceRequests;
import br.com.liberdadefamilia.workspace.WorkspaceScoped;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import java.util.ArrayList;
import java.util.List;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Puts the guard on every family route and lets guarded methods take a {@code WorkspaceMember} parameter. */
@Configuration(proxyBeanMethods = false)
class WorkspaceWebConfig implements WebMvcConfigurer {

    static {
        // Resolved by the guard, never sent by the client: it stays out of the OpenAPI contract.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(WorkspaceMember.class);
    }

    private final WorkspaceRepository workspaces;
    private final Modules modules;

    WorkspaceWebConfig(WorkspaceRepository workspaces, Modules modules) {
        this.workspaces = workspaces;
        this.modules = modules;
    }

    /**
     * Documents {@code {ws}} on every guarded handler. The handlers never declare it, since the guard reads it, so the
     * OpenAPI contract would otherwise leave it out.
     */
    @Bean
    GlobalOperationCustomizer workspacePathParameter() {
        return (operation, handler) -> {
            List<Parameter> parameters = operation.getParameters() == null ? List.of() : operation.getParameters();
            boolean declared = parameters.stream()
                    .anyMatch(parameter -> "ws".equals(parameter.getName()) && "path".equals(parameter.getIn()));
            if (handler.hasMethodAnnotation(WorkspaceScoped.class) && !declared) {
                List<Parameter> withWs = new ArrayList<>();
                withWs.add(new PathParameter()
                        .name("ws")
                        .description("Workspace (family) id")
                        .schema(new UUIDSchema()));
                withWs.addAll(parameters);
                operation.setParameters(withWs);
            }
            return operation;
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // A trailing "/**" also matches no segment at all, so /v1/workspaces/{ws} itself is guarded too.
        registry.addInterceptor(new WorkspaceGuardInterceptor(
                        workspaces, modules, SecurityContextHolder.getContextHolderStrategy()))
                .addPathPatterns("/v1/workspaces/*/**")
                .order(WorkspaceRequests.GUARD_ORDER);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new WorkspaceMemberArgumentResolver());
    }
}

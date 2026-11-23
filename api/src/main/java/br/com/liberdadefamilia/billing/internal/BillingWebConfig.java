package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.workspace.WorkspaceRequests;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Puts the plan guard on the family routes, right after the family guard. */
@Configuration(proxyBeanMethods = false)
class BillingWebConfig implements WebMvcConfigurer {

    private final Plans plans;

    BillingWebConfig(Plans plans) {
        this.plans = plans;
    }

    /**
     * The contract says {@code openOrder} may be null. A bare {@code $ref} cannot carry that, so it is wrapped:
     * {@code nullable} next to an {@code allOf} of the order.
     */
    @Bean
    OpenApiCustomizer nullableOpenOrder() {
        return openApi -> {
            Schema<?> details = openApi.getComponents().getSchemas().get("PlanDetails");
            if (details != null && details.getProperties() != null) {
                Schema<?> order = details.getProperties().get("openOrder");
                if (order != null && order.get$ref() != null) {
                    Schema<?> wrapped = new ComposedSchema().allOf(List.of(new Schema<>().$ref(order.get$ref())));
                    wrapped.setNullable(true);
                    details.getProperties().put("openOrder", wrapped);
                }
            }
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PlanGuardInterceptor(plans))
                .addPathPatterns("/v1/workspaces/*/**")
                .order(WorkspaceRequests.GUARD_ORDER + 100);
    }
}

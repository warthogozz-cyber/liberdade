package br.com.liberdadefamilia.indicators.internal;

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contract fixes for the indicator views. */
@Configuration(proxyBeanMethods = false)
class IndicatorsOpenApiConfig {

    private record NullableRef(String schema, String property) {}

    private static final List<NullableRef> NULLABLE_REFS = List.of(new NullableRef("IndicatorView", "variation"));

    /** A bare {@code $ref} cannot say it may be null, so it is wrapped: {@code nullable} next to an {@code allOf}. */
    @Bean
    OpenApiCustomizer indicatorsNullableRefs() {
        return openApi -> {
            for (NullableRef each : NULLABLE_REFS) {
                Schema<?> owner = openApi.getComponents().getSchemas().get(each.schema());
                if (owner == null || owner.getProperties() == null) {
                    continue;
                }
                Schema<?> property = owner.getProperties().get(each.property());
                if (property != null && property.get$ref() != null) {
                    Schema<?> wrapped = new ComposedSchema().allOf(List.of(new Schema<>().$ref(property.get$ref())));
                    wrapped.setNullable(true);
                    owner.getProperties().put(each.property(), wrapped);
                }
            }
        };
    }
}

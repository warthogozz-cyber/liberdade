package br.com.liberdadefamilia.planning.internal;

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contract fixes for the planning views. */
@Configuration(proxyBeanMethods = false)
class PlanningOpenApiConfig {

    private record NullableRef(String schema, String property) {}

    /** The properties that hold a nullable object; later planning views append their own. */
    private static final List<NullableRef> NULLABLE_REFS = List.of(
            new NullableRef("BudgetLineView", "overBy"),
            new NullableRef("GoalView", "suggestedMonthly"),
            new NullableRef("UpdateGoalRequest", "targetAmount"),
            new NullableRef("ReserveView", "averageMonthlyCost"),
            new NullableRef("ReserveView", "missing"),
            new NullableRef("ReserveView", "manualMonthlyCost"),
            new NullableRef("UpdateReserveRequest", "manualMonthlyCost"));

    /**
     * The month properties that may be null. A month is described by one shared schema, which carries no
     * {@code nullable}; each of these gets a nullable copy of it.
     */
    private static final List<NullableRef> NULLABLE_MONTHS = List.of(
            new NullableRef("GoalView", "dueOn"),
            new NullableRef("CreateGoalRequest", "dueOn"),
            new NullableRef("UpdateGoalRequest", "dueOn"));

    /**
     * The contract says these properties may be null. A bare {@code $ref} cannot carry that, so each is wrapped:
     * {@code nullable} next to an {@code allOf} of the object.
     */
    @Bean
    OpenApiCustomizer planningNullableRefs() {
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
            for (NullableRef each : NULLABLE_MONTHS) {
                Schema<?> owner = openApi.getComponents().getSchemas().get(each.schema());
                if (owner == null || owner.getProperties() == null) {
                    continue;
                }
                Schema<?> month = owner.getProperties().get(each.property());
                if (month != null) {
                    Schema<String> copy = new StringSchema().pattern(month.getPattern());
                    copy.setExample(month.getExample());
                    copy.setNullable(true);
                    owner.getProperties().put(each.property(), copy);
                }
            }
        };
    }
}

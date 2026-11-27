package br.com.liberdadefamilia.markets.internal;

import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contract fixes for the market views. */
@Configuration(proxyBeanMethods = false)
class MarketsOpenApiConfig {

    private record NullableRef(String schema, String property) {}

    private static final List<NullableRef> NULLABLE_REFS = List.of(
            new NullableRef("FamilyItem", "kind"),
            new NullableRef("UnlocatedItem", "kind"),
            new NullableRef("MarketCardView", "kind"),
            new NullableRef("MarketCardView", "address"),
            new NullableRef("MarketCardView", "website"),
            new NullableRef("MarketCardView", "instagram"),
            new NullableRef("MarketCardView", "flyer"),
            new NullableRef("SaveReceiptRequest", "market"),
            new NullableRef("SaveReceiptRequest", "newMarket"));

    /** A bare {@code $ref} cannot say the property may be null, so it is wrapped: {@code nullable} next to an allOf. */
    @Bean
    OpenApiCustomizer marketsNullableRefs() {
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
            nullableItems(openApi, "CompareItemView", "prices");
        };
    }

    /** The same wrapping for the elements of an array: a price that is null where the market has none. */
    private static void nullableItems(io.swagger.v3.oas.models.OpenAPI openApi, String schema, String property) {
        Schema<?> owner = openApi.getComponents().getSchemas().get(schema);
        if (owner == null || owner.getProperties() == null) {
            return;
        }
        Schema<?> array = owner.getProperties().get(property);
        if (array == null || array.getItems() == null || array.getItems().get$ref() == null) {
            return;
        }
        Schema<?> wrapped = new ComposedSchema()
                .allOf(List.of(new Schema<>().$ref(array.getItems().get$ref())));
        wrapped.setNullable(true);
        array.setItems(wrapped);
    }
}

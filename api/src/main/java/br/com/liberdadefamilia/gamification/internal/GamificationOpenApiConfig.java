package br.com.liberdadefamilia.gamification.internal;

import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contract fixes for the achievement views. */
@Configuration(proxyBeanMethods = false)
class GamificationOpenApiConfig {

    /** Lifts the inline key enum into a named {@code AchievementKey} schema, so the web app can import the type. */
    @Bean
    OpenApiCustomizer achievementKeyEnum() {
        return openApi -> {
            Schema<?> view = openApi.getComponents().getSchemas().get("AchievementView");
            if (view == null || view.getProperties() == null) {
                return;
            }
            Schema<?> key = view.getProperties().get("key");
            if (key == null || key.getEnum() == null) {
                return;
            }
            openApi.getComponents().addSchemas("AchievementKey", key);
            view.getProperties().put("key", new Schema<>().$ref("#/components/schemas/AchievementKey"));
        };
    }
}

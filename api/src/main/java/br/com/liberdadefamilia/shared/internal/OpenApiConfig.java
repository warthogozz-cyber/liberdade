package br.com.liberdadefamilia.shared.internal;

import br.com.liberdadefamilia.shared.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.servers.Server;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI contract the web generates its types from (D32). The types Jackson writes as text are described as text,
 * so the contract matches the JSON on the wire.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    static {
        SpringDocUtils.getConfig()
                .replaceWithClass(Money.class, MoneySchema.class)
                .replaceWithSchema(
                        YearMonth.class,
                        new StringSchema().pattern("^\\d{4}-(0[1-9]|1[0-2])$").example("2026-11"))
                .replaceWithSchema(
                        Currency.class, new StringSchema().pattern("^[A-Z]{3}$").example("BRL"))
                .replaceWithSchema(ZoneId.class, new StringSchema().example("America/Fortaleza"));
    }

    /** A fixed server, so the contract does not depend on the host that served it. */
    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Liberdade API").version("v1"))
                .servers(List.of(new Server().url("/")));
    }

    /** {@link Money} as {@link MoneyJacksonComponent} writes it. Only the contract uses this record. */
    @Schema(name = "Money")
    private record MoneySchema(
            @Schema(requiredMode = RequiredMode.REQUIRED, pattern = "^-?\\d+(\\.\\d{1,4})?$", example = "12.34")
            String amount,

            @Schema(requiredMode = RequiredMode.REQUIRED, pattern = "^[A-Z]{3}$", example = "BRL")
            String currency) {}
}

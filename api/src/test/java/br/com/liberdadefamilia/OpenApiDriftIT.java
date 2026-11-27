package br.com.liberdadefamilia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.core.util.Separators.Spacing;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code api/openapi.json} is the contract the web generates its types from (D32), so it must match what the API
 * serves. With {@code UPDATE_OPENAPI=true} the test rewrites the file instead of comparing it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDriftIT {

    /** Gradle runs the tests from {@code api/}. */
    private static final Path CONTRACT = Path.of("openapi.json");

    private static final JsonMapper JSON = JsonMapper.shared();

    /** Two spaces and LF on every machine, like {@code JSON.stringify(contract, null, 2)}. */
    private static final DefaultPrettyPrinter PRETTY = new DefaultPrettyPrinter(Separators.createDefaultInstance()
                    .withObjectNameValueSpacing(Spacing.AFTER)
                    .withObjectEmptySeparator("")
                    .withArrayEmptySeparator(""))
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n"));

    @Autowired
    MockMvcTester mvc;

    @Test
    void committedContractIsCurrent() throws IOException {
        var result = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(result).hasStatusOk();
        JsonNode served = JSON.readTree(result.getResponse().getContentAsByteArray());

        if ("true".equals(System.getenv("UPDATE_OPENAPI"))) {
            Files.writeString(CONTRACT, JSON.writer().with(PRETTY).writeValueAsString(served) + "\n");
            return;
        }
        CommittedContract.assertMatches(CONTRACT, served);
    }

    /** Every quantity is decimal text; the schema says which text the API accepts, so clients can check it. */
    @Test
    void quantitiesCarryTheDecimalPattern() {
        var result = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(result).hasStatusOk();
        JsonNode served = JSON.readTree(result.getResponse().getContentAsByteArray());

        java.util.List<String> without = new java.util.ArrayList<>();
        int checked = 0;
        for (var schema : served.path("components").path("schemas").properties()) {
            JsonNode quantity = schema.getValue().path("properties").path("quantity");
            if (quantity.isMissingNode()) {
                continue;
            }
            checked++;
            if (!"^\\d{1,15}(\\.\\d{1,4})?$".equals(quantity.path("pattern").asString())) {
                without.add(schema.getKey());
            }
        }
        org.assertj.core.api.Assertions.assertThat(checked).isGreaterThanOrEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(without).isEmpty();
    }
}

package br.com.liberdadefamilia;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Compares the committed {@code api/openapi.json} with the contract the API serves. */
final class CommittedContract {

    static final String OUT_OF_DATE = "api/openapi.json is out of date. Run: "
            + "UPDATE_OPENAPI=true ./gradlew test --rerun --tests '*OpenApiDriftIT'";

    private static final JsonMapper JSON = JsonMapper.shared();

    private CommittedContract() {}

    /** Fails with {@link #OUT_OF_DATE} unless {@code file} holds the served contract. */
    static void assertMatches(Path file, JsonNode served) {
        assertThat(read(file)).withFailMessage(OUT_OF_DATE).isEqualTo(served);
    }

    /** A missing file and an unparseable one (a bad merge, say) both read as nothing, so both fail as out of date. */
    private static @Nullable JsonNode read(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return JSON.readTree(file);
        } catch (JacksonException unparseable) {
            return null;
        }
    }
}

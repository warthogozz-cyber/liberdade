package br.com.liberdadefamilia;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A contract file that cannot be read fails like a stale one: with the command that regenerates it. */
class CommittedContractTest {

    private static final JsonNode SERVED = JsonMapper.shared().readTree("{\"openapi\": \"3.1.0\"}");

    @TempDir
    Path dir;

    @Test
    void unparseableFileIsOutOfDate() throws IOException {
        Path file = Files.writeString(dir.resolve("openapi.json"), "{\n<<<<<<< HEAD\n  \"openapi\": \"3.1.0\"\n");

        assertThatThrownBy(() -> CommittedContract.assertMatches(file, SERVED))
                .isInstanceOf(AssertionError.class)
                .hasMessage(CommittedContract.OUT_OF_DATE);
    }

    @Test
    void missingFileIsOutOfDate() {
        Path file = dir.resolve("openapi.json");

        assertThatThrownBy(() -> CommittedContract.assertMatches(file, SERVED))
                .isInstanceOf(AssertionError.class)
                .hasMessage(CommittedContract.OUT_OF_DATE);
    }
}

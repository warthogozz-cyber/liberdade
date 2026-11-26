package br.com.liberdadefamilia.markets.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

/** Builds datasets in memory from the sample the workflow script's own test uses, so reader and producer agree. */
final class OsmTestData {

    /** The sample shared with {@code scripts/osm/test_to_csv.py}; Gradle runs the tests from {@code api/}. */
    static final Path SAMPLE = Path.of("..", "scripts", "osm", "testdata", "expected.csv");

    private OsmTestData() {}

    static String sampleCsv() {
        try {
            return Files.readString(SAMPLE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The sample cut to its header and the first {@code rows} rows. */
    static String sampleCsv(int rows) {
        String[] lines = sampleCsv().split("\n");
        return String.join("\n", Arrays.copyOfRange(lines, 0, rows + 1)) + "\n";
    }

    static byte[] gzip(String csv) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream zipped = new GZIPOutputStream(bytes)) {
                zipped.write(csv.getBytes(StandardCharsets.UTF_8));
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A manifest that tells the truth about {@code csv} and its archive. */
    static OsmManifest manifest(Instant osmTimestamp, String csv, byte[] gz) {
        int rows = (int) csv.lines().filter(line -> !line.isBlank()).count() - 1;
        return new OsmManifest(osmTimestamp, rows, sha256(gz));
    }
}

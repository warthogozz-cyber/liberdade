package br.com.liberdadefamilia.markets.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

/**
 * Six invented markets around (-10.00000, -50.00000) for dev, e2e and tests (D16). One has no name and one has an
 * Instagram link. It goes through the same reader and checks as the real file.
 */
class FakeOsmDataset implements OsmDataset {

    private static final Instant TIMESTAMP = Instant.parse("2026-01-01T00:00:00Z");
    private static final String[] KINDS = {
        "SUPERMARKET", "CONVENIENCE", "GREENGROCER", "MARKETPLACE", "SUPERMARKET", "CONVENIENCE"
    };

    private final byte[] gz = build();
    private final OsmManifest manifest = new OsmManifest(TIMESTAMP, KINDS.length, sha256(gz));

    @Override
    public OsmManifest manifest() {
        return manifest;
    }

    @Override
    public InputStream open() {
        return new ByteArrayInputStream(gz);
    }

    private static byte[] build() {
        StringBuilder csv = new StringBuilder(String.join(",", OsmCsvReader.HEADER)).append('\n');
        for (int i = 0; i < KINDS.length; i++) {
            int n = i + 1;
            String name = n == 4 ? "" : "Mercado Exemplo " + n;
            String instagram = n == 5 ? "https://www.instagram.com/exemplo.loja/" : "";
            csv.append(String.format(
                    Locale.ROOT,
                    "n,%d,%s,%s,,%.5f,%.5f,,%s,,Rua Exemplo,%d,,\n",
                    9000 + n,
                    KINDS[i],
                    name,
                    -10.0 + (n - 3) * 0.002,
                    -50.0 + (n - 3) * 0.003,
                    instagram,
                    n * 10));
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream zipped = new GZIPOutputStream(bytes)) {
                zipped.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

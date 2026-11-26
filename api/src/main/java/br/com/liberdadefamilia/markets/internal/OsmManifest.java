package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.internal.OsmImportException.Reason;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The publisher's account of the file (D15): when the data is from, how many rows it has, and the SHA-256 of the
 * compressed file.
 */
public record OsmManifest(Instant osmTimestamp, int rowCount, String sha256) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Pattern HEX_64 = Pattern.compile("[0-9a-fA-F]{64}");

    /** Reads {@code manifest.json}; anything that is not what the workflow writes is {@code BAD_MANIFEST}. */
    static OsmManifest parse(String json) throws OsmImportException {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode timestamp = root.get("osmTimestamp");
            JsonNode count = root.get("rowCount");
            JsonNode sha = root.get("sha256");
            if (timestamp == null
                    || !timestamp.isString()
                    || count == null
                    || !count.isIntegralNumber()
                    || !count.canConvertToInt()
                    || count.intValue() < 0
                    || sha == null
                    || !sha.isString()
                    || !HEX_64.matcher(sha.stringValue()).matches()) {
                throw new OsmImportException(Reason.BAD_MANIFEST);
            }
            return new OsmManifest(
                    OffsetDateTime.parse(timestamp.stringValue()).toInstant(),
                    count.intValue(),
                    sha.stringValue().toLowerCase(java.util.Locale.ROOT));
        } catch (JacksonException | DateTimeParseException e) {
            throw new OsmImportException(Reason.BAD_MANIFEST);
        }
    }
}

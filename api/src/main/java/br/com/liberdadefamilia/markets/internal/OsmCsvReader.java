package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.GeoPoint;
import br.com.liberdadefamilia.markets.domain.LocationTooPreciseException;
import br.com.liberdadefamilia.markets.domain.MarketKind;
import br.com.liberdadefamilia.markets.internal.OsmImportException.Reason;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Reads {@code markets-br.csv.gz} in a stream (D15): UTF-8, comma, RFC 4180 quotes, with the header the workflow
 * writes. Anything that is not what the workflow writes is {@code BAD_ROW}: the file is wrong, so nothing is kept. An
 * empty field is {@code null}. Errors carry no row text.
 */
final class OsmCsvReader {

    static final List<String> HEADER = List.of(
            "osm_type",
            "osm_id",
            "kind",
            "name",
            "brand",
            "lat",
            "lon",
            "website",
            "instagram",
            "opening_hours",
            "street",
            "housenumber",
            "suburb",
            "city");

    private static final int DECIMALS = 5;

    private OsmCsvReader() {}

    /** Hands each row to {@code sink} and returns how many there were. */
    static int read(InputStream gz, Consumer<OsmMarketRow> sink) throws OsmImportException {
        try (Reader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(gz),
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            List<String> header = nextRecord(reader);
            if (!HEADER.equals(header)) {
                throw new OsmImportException(Reason.BAD_ROW);
            }
            int rows = 0;
            for (List<String> fields = nextRecord(reader); fields != null; fields = nextRecord(reader)) {
                if (fields.size() == 1 && fields.get(0) == null) {
                    continue; // a blank line
                }
                sink.accept(toRow(fields));
                rows++;
            }
            return rows;
        } catch (OsmImportException e) {
            throw e;
        } catch (IOException e) {
            // Not a gzip, a broken one, or text that is not UTF-8: the file is wrong.
            throw new OsmImportException(Reason.BAD_ROW);
        }
    }

    private static OsmMarketRow toRow(List<String> f) throws OsmImportException {
        if (f.size() != HEADER.size()) {
            throw new OsmImportException(Reason.BAD_ROW);
        }
        try {
            String type = f.get(0);
            if (type == null || !(type.equals("n") || type.equals("w") || type.equals("r"))) {
                throw new OsmImportException(Reason.BAD_ROW);
            }
            long id = Long.parseLong(required(f.get(1)));
            MarketKind kind = MarketKind.valueOf(required(f.get(2)));
            BigDecimal lat = new BigDecimal(required(f.get(5)));
            BigDecimal lon = new BigDecimal(required(f.get(6)));
            new GeoPoint(lat, lon).requireScale(DECIMALS);
            return new OsmMarketRow(
                    type.charAt(0),
                    id,
                    kind,
                    f.get(3),
                    f.get(4),
                    lat,
                    lon,
                    f.get(7),
                    f.get(8),
                    f.get(9),
                    f.get(10),
                    f.get(11),
                    f.get(12),
                    f.get(13));
        } catch (IllegalArgumentException | LocationTooPreciseException e) {
            // NumberFormatException, an unknown kind and an out-of-range point are all IllegalArgumentException.
            throw new OsmImportException(Reason.BAD_ROW);
        }
    }

    private static String required(String value) {
        if (value == null) {
            throw new IllegalArgumentException("missing");
        }
        return value;
    }

    /** The next record, or {@code null} at the end of the input. A record may span lines inside quotes. */
    private static List<String> nextRecord(Reader in) throws IOException {
        int c = in.read();
        if (c < 0) {
            return null;
        }
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean wasQuoted = false;
        while (true) {
            if (quoted) {
                if (c < 0) {
                    throw new OsmImportException(Reason.BAD_ROW);
                }
                if (c == '"') {
                    int next = in.read();
                    if (next == '"') {
                        field.append('"');
                        c = in.read();
                    } else {
                        quoted = false;
                        c = next;
                    }
                } else {
                    field.append((char) c);
                    c = in.read();
                }
                continue;
            }
            if (c < 0 || c == '\n') {
                fields.add(finish(field));
                return fields;
            }
            if (c == '\r') {
                int next = in.read();
                if (next == '\n' || next < 0) {
                    fields.add(finish(field));
                    return fields;
                }
                field.append('\r');
                c = next;
                continue;
            }
            if (c == ',') {
                fields.add(finish(field));
                wasQuoted = false;
                c = in.read();
                continue;
            }
            if (c == '"' && field.isEmpty() && !wasQuoted) {
                quoted = true;
                wasQuoted = true;
                c = in.read();
                continue;
            }
            field.append((char) c);
            c = in.read();
        }
    }

    private static String finish(StringBuilder field) {
        String text = field.toString();
        field.setLength(0);
        return text.isEmpty() ? null : text;
    }
}

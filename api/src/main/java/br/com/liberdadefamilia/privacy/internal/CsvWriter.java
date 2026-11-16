package br.com.liberdadefamilia.privacy.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Semicolon-separated CSV that opens correctly in a spreadsheet with a Brazilian locale: UTF-8 with a byte order
 * mark, {@code \r\n} line ends, a comma as the decimal separator. A cell that a spreadsheet would run as a formula is
 * defused with a leading apostrophe.
 */
final class CsvWriter {

    private static final String BOM = "\uFEFF";
    private static final String EOL = "\r\n";

    /** The cell as text: formula-neutralized, and quoted when it holds a semicolon, a quote or a line break. */
    static String cell(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = value;
        char first = text.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            text = "'" + text;
        }
        boolean quote =
                text.indexOf(';') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
        return quote ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    /** {@code 1234,56}: two decimals, a comma, no thousands separator. */
    static String amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN).toPlainString().replace('.', ',');
    }

    byte[] toBytes(List<String> header, List<List<String>> rows) {
        return toBytes(header, rows, Set.of());
    }

    /**
     * The same, where the columns at {@code amountColumns} hold what {@link #amount} wrote: those are numbers, so a
     * negative one is not a formula and is not prefixed. Every other cell goes through {@link #cell}.
     */
    byte[] toBytes(List<String> header, List<List<String>> rows, Set<Integer> amountColumns) {
        StringBuilder out = new StringBuilder(BOM);
        line(out, header, Set.of());
        for (List<String> row : rows) {
            line(out, row, amountColumns);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder out, List<String> cells, Set<Integer> amountColumns) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(';');
            }
            out.append(amountColumns.contains(i) ? cells.get(i) : cell(cells.get(i)));
        }
        out.append(EOL);
    }
}

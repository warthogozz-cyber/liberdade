package br.com.liberdadefamilia.privacy.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Semicolon-separated CSV that opens correctly in a spreadsheet with a Brazilian locale: UTF-8 with a byte order
 * mark, {@code \r\n} line ends, a comma as the decimal separator. A cell that a spreadsheet would run as a formula is
 * defused with a leading apostrophe.
 */
final class CsvWriter {

    /** What {@link #amount} writes for a negative value: a number, not a formula, so it stays a number. */
    private static final Pattern NEGATIVE_AMOUNT = Pattern.compile("-[0-9]+,[0-9]{2}");

    private static final String BOM = "\uFEFF";
    private static final String EOL = "\r\n";

    /** The cell as text: formula-neutralized, and quoted when it holds a semicolon, a quote or a line break. */
    static String cell(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = value;
        char first = text.charAt(0);
        if (NEGATIVE_AMOUNT.matcher(text).matches()) {
            return text;
        }
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
        StringBuilder out = new StringBuilder(BOM);
        line(out, header);
        for (List<String> row : rows) {
            line(out, row);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(';');
            }
            out.append(cell(cells.get(i)));
        }
        out.append(EOL);
    }
}

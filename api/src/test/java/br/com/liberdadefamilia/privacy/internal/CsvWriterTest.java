package br.com.liberdadefamilia.privacy.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    void amountHasTwoDecimalsAndACommaForDecimals() {
        assertThat(CsvWriter.amount(new BigDecimal("1234.56"))).isEqualTo("1234,56");
        assertThat(CsvWriter.amount(new BigDecimal("5"))).isEqualTo("5,00");
        assertThat(CsvWriter.amount(new BigDecimal("-0.5000"))).isEqualTo("-0,50");
    }

    @Test
    void formulaCellsAreDefusedAndQuoted() {
        assertThat(CsvWriter.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
    }

    @Test
    void everyFormulaStarterGetsAnApostrophe() {
        assertThat(CsvWriter.cell("-10")).isEqualTo("'-10");
        assertThat(CsvWriter.cell("+1")).isEqualTo("'+1");
        assertThat(CsvWriter.cell("@a")).isEqualTo("'@a");
        assertThat(CsvWriter.cell("\tx")).isEqualTo("'\tx");
        assertThat(CsvWriter.cell("\rx")).startsWith("\"'\r");
    }

    @Test
    void aNegativeAmountStaysANumber() {
        assertThat(CsvWriter.cell(CsvWriter.amount(new BigDecimal("-1352.56")))).isEqualTo("-1352,56");
        assertThat(CsvWriter.cell("-10")).isEqualTo("'-10");
    }

    @Test
    void separatorsQuotesAndBreaksAreQuoted() {
        assertThat(CsvWriter.cell("Pão; leite")).isEqualTo("\"Pão; leite\"");
        assertThat(CsvWriter.cell("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(CsvWriter.cell("a\nb")).isEqualTo("\"a\nb\"");
        assertThat(CsvWriter.cell("Pão")).isEqualTo("Pão");
        assertThat(CsvWriter.cell("")).isEmpty();
        assertThat(CsvWriter.cell(null)).isEmpty();
    }

    @Test
    void bytesAreUtf8WithBomAndWindowsLineEnds() {
        byte[] bytes = new CsvWriter().toBytes(List.of("nome", "valor"), List.of(List.of("Pão", "1,00")));

        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8))
                .isEqualTo("nome;valor\r\nPão;1,00\r\n");
    }
}

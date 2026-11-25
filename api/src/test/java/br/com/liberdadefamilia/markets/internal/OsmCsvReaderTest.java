package br.com.liberdadefamilia.markets.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import br.com.liberdadefamilia.markets.domain.MarketKind;
import br.com.liberdadefamilia.markets.internal.OsmImportException.Reason;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The reader against what {@code scripts/osm/to_csv.py} writes. */
class OsmCsvReaderTest {

    private static final String HEADER =
            "osm_type,osm_id,kind,name,brand,lat,lon,website,instagram,opening_hours,street,housenumber,suburb,city\n";
    private static final String OK_ROW = "n,1,SUPERMARKET,Mercado Exemplo,,-10.00000,-50.00000,,,,,,,\n";

    private static List<OsmMarketRow> read(String csv) throws OsmImportException {
        List<OsmMarketRow> rows = new ArrayList<>();
        int count = OsmCsvReader.read(new ByteArrayInputStream(OsmTestData.gzip(csv)), rows::add);
        assertThat(count).isEqualTo(rows.size());
        return rows;
    }

    private static Reason reasonOf(String csv) {
        return catchThrowableOfType(OsmImportException.class, () -> read(csv)).reason();
    }

    @Test
    void readsTheSharedSample() throws Exception {
        List<OsmMarketRow> rows = read(OsmTestData.sampleCsv());

        assertThat(rows).hasSize(4);
        OsmMarketRow supermarket = rows.get(0);
        assertThat(supermarket.type()).isEqualTo('n');
        assertThat(supermarket.id()).isEqualTo(1001L);
        assertThat(supermarket.kind()).isEqualTo(MarketKind.SUPERMARKET);
        assertThat(supermarket.name()).isEqualTo("Mercado Exemplo Um");
        assertThat(supermarket.brand()).isEqualTo("Rede Exemplo");
        assertThat(supermarket.lat()).isEqualByComparingTo(new BigDecimal("-10.00034"));
        assertThat(supermarket.lon()).isEqualByComparingTo(new BigDecimal("-50.00012"));
        assertThat(supermarket.website()).isEqualTo("https://example.test/um");
        assertThat(supermarket.instagram()).isNull();
        assertThat(supermarket.openingHours()).isEqualTo("Mo-Sa 08:00-20:00");
        assertThat(supermarket.street()).isEqualTo("Rua Exemplo");
        assertThat(supermarket.housenumber()).isEqualTo("100");
        assertThat(supermarket.suburb()).isEqualTo("Bairro Exemplo");
        assertThat(supermarket.city()).isEqualTo("Cidade Exemplo");

        OsmMarketRow way = rows.get(1);
        assertThat(way.type()).isEqualTo('w');
        assertThat(way.id()).isEqualTo(1001L);
        assertThat(way.kind()).isEqualTo(MarketKind.GREENGROCER);
        assertThat(way.brand()).isNull();
        assertThat(way.openingHours()).isNull();

        OsmMarketRow relation = rows.get(2);
        assertThat(relation.type()).isEqualTo('r');
        assertThat(relation.kind()).isEqualTo(MarketKind.MARKETPLACE);
        assertThat(relation.website()).isNull();
        assertThat(relation.street()).isEqualTo("Rua Exemplo");

        OsmMarketRow unnamed = rows.get(3);
        assertThat(unnamed.kind()).isEqualTo(MarketKind.CONVENIENCE);
        assertThat(unnamed.name()).isNull();
        assertThat(unnamed.instagram()).isEqualTo("https://www.instagram.com/exemplo.loja/");
    }

    @Test
    void badKindIsBadRow() {
        assertThat(reasonOf(HEADER + "n,1,BAKERY,Padaria Exemplo,,-10.00000,-50.00000,,,,,,,\n"))
                .isEqualTo(Reason.BAD_ROW);
    }

    @Test
    void otherBadRowsAreBadRows() {
        assertThat(reasonOf(HEADER + OK_ROW + "n,2,SUPERMARKET,Mercado Exemplo,,-10.00000\n"))
                .as("a column is missing")
                .isEqualTo(Reason.BAD_ROW);
        assertThat(reasonOf(HEADER + "n,2,SUPERMARKET,Mercado Exemplo,,-91.00000,-50.00000,,,,,,,\n"))
                .as("latitude out of range")
                .isEqualTo(Reason.BAD_ROW);
        assertThat(reasonOf(HEADER + "n,2,SUPERMARKET,Mercado Exemplo,,-10.00000,-181.00000,,,,,,,\n"))
                .as("longitude out of range")
                .isEqualTo(Reason.BAD_ROW);
        assertThat(reasonOf(HEADER + "n,2,SUPERMARKET,Mercado Exemplo,,-10.000001,-50.00000,,,,,,,\n"))
                .as("more than five decimals")
                .isEqualTo(Reason.BAD_ROW);
        assertThat(reasonOf(HEADER + "x,2,SUPERMARKET,Mercado Exemplo,,-10.00000,-50.00000,,,,,,,\n"))
                .as("unknown osm type")
                .isEqualTo(Reason.BAD_ROW);
        assertThat(reasonOf(HEADER + "n,abc,SUPERMARKET,Mercado Exemplo,,-10.00000,-50.00000,,,,,,,\n"))
                .as("id is not a number")
                .isEqualTo(Reason.BAD_ROW);
        assertThat(reasonOf(HEADER + "n,2,SUPERMARKET,Mercado Exemplo,,,-50.00000,,,,,,,\n"))
                .as("latitude is empty")
                .isEqualTo(Reason.BAD_ROW);
        assertThat(reasonOf("osm_type,osm_id\nn,1\n")).as("wrong header").isEqualTo(Reason.BAD_ROW);
        assertThat(reasonOf("")).as("empty file").isEqualTo(Reason.BAD_ROW);
    }

    @Test
    void notAGzipIsBadRow() {
        OsmImportException failure = catchThrowableOfType(
                OsmImportException.class,
                () -> OsmCsvReader.read(new ByteArrayInputStream("plain".getBytes()), row -> {}));

        assertThat(failure.reason()).isEqualTo(Reason.BAD_ROW);
    }

    @Test
    void readsQuotedFields() throws Exception {
        String csv = HEADER
                + "n,7,SUPERMARKET,\"Mercado \"\"Exemplo\"\", Centro\",,-10.00000,-50.00000,,,\"Mo-Fr 08:00-18:00;\nSa 08:00-12:00\",,,,\r\n"
                + "n,8,CONVENIENCE,Loja Exemplo,,-10.00001,-50.00001,,,,,,,";

        List<OsmMarketRow> rows = read(csv);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).name()).isEqualTo("Mercado \"Exemplo\", Centro");
        assertThat(rows.get(0).openingHours()).isEqualTo("Mo-Fr 08:00-18:00;\nSa 08:00-12:00");
        assertThat(rows.get(1).id()).isEqualTo(8L);
    }

    @Test
    void headerOnlyIsAnEmptyDataset() throws Exception {
        assertThat(read(HEADER)).isEmpty();
    }
}

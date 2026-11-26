package br.com.liberdadefamilia.markets.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The fake source that dev, e2e and the tests run on. */
class FakeOsmDatasetTest {

    @Test
    void servesSixInventedMarketsThatPassTheSameChecksAsTheRealOne() throws Exception {
        FakeOsmDataset dataset = new FakeOsmDataset();
        List<OsmMarketRow> rows = new ArrayList<>();

        OsmManifest manifest = dataset.manifest();
        byte[] bytes;
        try (var in = dataset.open()) {
            bytes = in.readAllBytes();
        }
        int count = OsmCsvReader.read(new ByteArrayInputStream(bytes), rows::add);

        assertThat(count).isEqualTo(6).isEqualTo(manifest.rowCount());
        assertThat(OsmTestData.sha256(bytes)).isEqualTo(manifest.sha256());
        assertThat(rows)
                .extracting(OsmMarketRow::name)
                .filteredOn(name -> name != null)
                .allMatch(name -> name.startsWith("Mercado Exemplo"));
        assertThat(rows).filteredOn(row -> row.name() == null).hasSize(1);
        assertThat(rows).filteredOn(row -> row.instagram() != null).hasSize(1);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.lat().doubleValue()).isBetween(-10.01, -9.99);
            assertThat(row.lon().doubleValue()).isBetween(-50.01, -49.99);
        });
    }

    @Test
    void theConfigPicksTheSourceByProperty() {
        MarketsSourceConfig config = new MarketsSourceConfig();
        MarketsProperties fake = new MarketsProperties(
                null, new MarketsProperties.Osm("fake", null, null, null, null, null), null, null);

        assertThat(config.osmDataset(fake)).isInstanceOf(FakeOsmDataset.class);
        assertThat(config.osmDataset(new MarketsProperties(null, null, null, null)))
                .isInstanceOf(GithubOsmDataset.class);
    }
}

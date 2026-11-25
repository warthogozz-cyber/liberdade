package br.com.liberdadefamilia.markets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MarketRefTest {

    @Test
    void parsesBothForms() {
        UUID id = UUID.randomUUID();

        assertThat(MarketRef.parse("osm-w-45")).contains(new OsmRef('w', 45));
        assertThat(MarketRef.parse("osm-n-123")).contains(new OsmRef('n', 123));
        assertThat(MarketRef.parse("osm-r-6")).contains(new OsmRef('r', 6));
        assertThat(MarketRef.parse(id.toString())).contains(new FamilyRef(id));
    }

    @Test
    void refusesOtherForms() {
        for (String bad : new String[] {
            "osm-x-1", "osm-n-", "abc", "", "osm-n-99999999999999999999", "osm-n--1", "OSM-n-1", "not-a-uuid-at-all"
        }) {
            assertThat(MarketRef.parse(bad)).as(bad).isEmpty();
        }
        assertThat(MarketRef.parse(null)).isEmpty();
    }

    @Test
    void writesTheOsmForm() {
        assertThat(new OsmRef('w', 45).asText()).isEqualTo("osm-w-45");
    }
}

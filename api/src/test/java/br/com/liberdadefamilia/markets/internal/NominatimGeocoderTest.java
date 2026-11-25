package br.com.liberdadefamilia.markets.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The Nominatim client against canned answers; nothing here touches the network. */
class NominatimGeocoderTest {

    private static final String BASE = "https://geo.test";
    private static final String BOT = "https://app.test/robo";

    private MockRestServiceServer server;
    private NominatimGeocoder geocoder;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MarketsProperties properties = new MarketsProperties(
                BOT,
                null,
                new MarketsProperties.Geocoding(true, "nominatim", BASE, null, null, Duration.ofSeconds(5)),
                null);
        geocoder = new NominatimGeocoder(builder, properties);
    }

    private static String result(String lat, String lon, String addressJson) {
        return "{\"lat\":\"" + lat + "\",\"lon\":\"" + lon + "\",\"display_name\":\"Rua Exemplo\",\"address\":"
                + addressJson + "}";
    }

    private void answer(String body) {
        server.expect(requestTo(startsWith(BASE + "/search")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void sendsTheRightQuery() throws Exception {
        server.expect(requestTo(startsWith(BASE + "/search")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("q", "Rua%20Exemplo%2010"))
                .andExpect(queryParam("format", "jsonv2"))
                .andExpect(queryParam("addressdetails", "1"))
                .andExpect(queryParam("limit", "5"))
                .andExpect(queryParam("countrycodes", "br"))
                .andExpect(header("User-Agent", "LiberdadeBot/1.0 (+" + BOT + ")"))
                .andExpect(header("Accept-Language", "pt-BR"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(geocoder.search("Rua Exemplo 10")).isEmpty();

        server.verify();
    }

    @Test
    void roundsAndLabels() throws Exception {
        answer("["
                + result(
                        "-10.0004567",
                        "-50.0005",
                        "{\"suburb\":\"Bairro Exemplo\",\"city\":\"Cidade Exemplo\",\"state\":\"Estado Exemplo\"}")
                + "]");

        List<GeocodedPlace> places = geocoder.search("Rua Exemplo 10");

        assertThat(places).hasSize(1);
        assertThat(places.get(0).point().lat().toPlainString()).isEqualTo("-10.000");
        assertThat(places.get(0).point().lon().toPlainString()).isEqualTo("-50.001"); // half up
        assertThat(places.get(0).label()).isEqualTo("Bairro Exemplo, Cidade Exemplo");
    }

    @Test
    void labelFallsBackToCityAndState() throws Exception {
        answer("[" + result("-10.0", "-50.0", "{\"town\":\"Cidade Exemplo\",\"state\":\"Estado Exemplo\"}") + "]");

        assertThat(geocoder.search("x").get(0).label()).isEqualTo("Cidade Exemplo, Estado Exemplo");
    }

    @Test
    void aLongNameIsCutToTheLabelLimit() throws Exception {
        String district = "Bairro Exemplo ".repeat(4).strip(); // 59 characters
        answer("[" + result("-10.0", "-50.0", "{\"suburb\":\"" + district + "\",\"city\":\"Cidade Exemplo\"}") + "]");

        String label = geocoder.search("x").get(0).label();

        assertThat(label).hasSizeLessThanOrEqualTo(40);
        assertThat(label).startsWith("Bairro Exemplo Bairro Exemplo");
    }

    @Test
    void badJsonFails() {
        answer("{}");

        assertThatThrownBy(() -> geocoder.search("Rua Exemplo 10")).isInstanceOf(GeocodingException.class);
    }

    @Test
    void aPointOffTheGlobeFails() {
        answer("[" + result("-95.0", "-50.0", "{\"city\":\"Cidade Exemplo\"}") + "]");

        assertThatThrownBy(() -> geocoder.search("Rua Exemplo 10")).isInstanceOf(GeocodingException.class);
    }

    @Test
    void aServerErrorFailsWithoutTheQuery() {
        server.expect(requestTo(startsWith(BASE + "/search"))).andRespond(withServerError());

        assertThatThrownBy(() -> geocoder.search("Rua Exemplo 10"))
                .isInstanceOf(GeocodingException.class)
                .hasMessageNotContaining("Exemplo");
    }
}

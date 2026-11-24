package br.com.liberdadefamilia.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.liberdadefamilia.indicators.internal.BcbIndicatorSource;
import br.com.liberdadefamilia.indicators.internal.FetchedPoint;
import br.com.liberdadefamilia.indicators.internal.IndicatorCode;
import br.com.liberdadefamilia.indicators.internal.IndicatorFetchException;
import br.com.liberdadefamilia.indicators.internal.IndicatorProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The Banco Central client against recorded responses; nothing here touches the network. */
class BcbIndicatorSourceTest {

    private static final String SGS = "https://sgs.test";
    private static final String PTAX = "https://ptax.test/v1";
    private static final String BOT = "https://app.test/robo";
    private static final String SGS_432 =
            SGS + "/dados/serie/bcdata.sgs.432/dados?formato=json&dataInicial=10/11/2026&dataFinal=20/11/2026";
    private static final String SGS_433 =
            SGS + "/dados/serie/bcdata.sgs.433/dados?formato=json&dataInicial=01/07/2026&dataFinal=30/11/2026";
    private static final String PTAX_URL = PTAX
            + "/odata/CotacaoDolarPeriodo(dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)"
            + "?@dataInicial='11-16-2026'&@dataFinalCotacao='11-20-2026'&$format=json";

    private MockRestServiceServer server;
    private BcbIndicatorSource source;
    private final List<Duration> waits = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        IndicatorProperties properties = new IndicatorProperties(
                "bcb",
                SGS,
                PTAX,
                BOT,
                Duration.ofSeconds(10),
                List.of(Duration.ofSeconds(2), Duration.ofSeconds(8)),
                Duration.ofSeconds(1));
        source = new BcbIndicatorSource(builder, properties, waits::add);
    }

    private static String fixture(String name) throws IOException {
        return new ClassPathResource("indicators/" + name).getContentAsString(StandardCharsets.UTF_8);
    }

    private List<FetchedPoint> fetchSelic() throws IndicatorFetchException {
        return source.fetch(IndicatorCode.SELIC_TARGET, "432", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 20));
    }

    private List<FetchedPoint> fetchIpca() throws IndicatorFetchException {
        return source.fetch(IndicatorCode.IPCA_MONTH, "433", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 11, 30));
    }

    private List<FetchedPoint> fetchDollar() throws IndicatorFetchException {
        return source.fetch(
                IndicatorCode.USD_PTAX_SELL,
                "CotacaoDolarPeriodo",
                LocalDate.of(2026, 11, 16),
                LocalDate.of(2026, 11, 20));
    }

    @Test
    void parsesSgs() throws Exception {
        server.expect(once(), requestTo(SGS_432))
                .andRespond(withSuccess(fixture("sgs-432-ok.json"), MediaType.APPLICATION_JSON));

        List<FetchedPoint> points = fetchSelic();

        assertThat(points).hasSize(11);
        assertThat(points.getFirst().refDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(points.getFirst().value()).isEqualByComparingTo(new BigDecimal("13.75"));
        assertThat(points.getLast().refDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(points).extracting(FetchedPoint::refDate).isSorted();
        server.verify();
    }

    @Test
    void parsesSgsMonthlyValuesWithSign() throws Exception {
        server.expect(once(), requestTo(SGS_433))
                .andRespond(withSuccess(fixture("sgs-433-ok.json"), MediaType.APPLICATION_JSON));

        List<FetchedPoint> points = fetchIpca();

        assertThat(points)
                .extracting(FetchedPoint::value)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(
                        new BigDecimal("0.07"),
                        new BigDecimal("-0.32"),
                        new BigDecimal("0.82"),
                        new BigDecimal("0.14"));
        assertThat(points)
                .extracting(FetchedPoint::refDate)
                .containsExactly(
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 10, 1));
    }

    @Test
    void sendsBothDatesAndTheBotAgent() throws Exception {
        server.expect(once(), requestTo(SGS_432))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("User-Agent", "LiberdadeBot/1.0 (+https://app.test/robo)"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        fetchSelic();

        server.verify();
    }

    @Test
    void parsesPtaxSellingRate() throws Exception {
        server.expect(once(), requestTo(PTAX_URL))
                .andExpect(header("User-Agent", "LiberdadeBot/1.0 (+https://app.test/robo)"))
                .andRespond(withSuccess(fixture("ptax-ok.json"), MediaType.APPLICATION_JSON));

        List<FetchedPoint> points = fetchDollar();

        assertThat(points)
                .extracting(FetchedPoint::refDate)
                .containsExactly(
                        LocalDate.of(2026, 11, 16),
                        LocalDate.of(2026, 11, 17),
                        LocalDate.of(2026, 11, 18),
                        LocalDate.of(2026, 11, 19));
        assertThat(points)
                .extracting(FetchedPoint::value)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(
                        new BigDecimal("5.73240"),
                        new BigDecimal("5.78510"),
                        new BigDecimal("5.81020"),
                        new BigDecimal("5.88890"));
    }

    @Test
    void lastQuoteOfTheDayWins() throws Exception {
        server.expect(once(), requestTo(PTAX_URL))
                .andRespond(withSuccess(fixture("ptax-two-quotes-same-day.json"), MediaType.APPLICATION_JSON));

        List<FetchedPoint> points = fetchDollar();

        assertThat(points).hasSize(4);
        FetchedPoint day17 = points.stream()
                .filter(p -> p.refDate().equals(LocalDate.of(2026, 11, 17)))
                .findFirst()
                .orElseThrow();
        assertThat(day17.value()).isEqualByComparingTo(new BigDecimal("5.79060"));
    }

    @Test
    void emptyWindowIsEmptyList() throws Exception {
        // The real API answers 404 with this body when the window has no data.
        server.expect(once(), requestTo(SGS_433))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("sgs-empty.json")));

        assertThat(fetchIpca()).isEmpty();
        assertThat(waits).isEmpty();
    }

    @Test
    void emptyArrayIsEmptyList() throws Exception {
        server.expect(once(), requestTo(SGS_432)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(fetchSelic()).isEmpty();
    }

    @Test
    void serverErrorRetriesTwiceThenFails() {
        server.expect(times(3), requestTo(SGS_432)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(this::fetchSelic)
                .isInstanceOfSatisfying(
                        IndicatorFetchException.class,
                        e -> assertThat(e.kind()).isEqualTo(IndicatorFetchException.Kind.HTTP_STATUS));
        assertThat(waits).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(8));
        server.verify();
    }

    @Test
    void serverErrorThenSuccess() throws Exception {
        server.expect(once(), requestTo(SGS_432)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(SGS_432))
                .andRespond(withSuccess(fixture("sgs-432-ok.json"), MediaType.APPLICATION_JSON));

        assertThat(fetchSelic()).hasSize(11);
        assertThat(waits).containsExactly(Duration.ofSeconds(2));
        server.verify();
    }

    @Test
    void clientErrorFailsAtOnce() {
        server.expect(once(), requestTo(SGS_432)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(this::fetchSelic)
                .isInstanceOfSatisfying(
                        IndicatorFetchException.class,
                        e -> assertThat(e.kind()).isEqualTo(IndicatorFetchException.Kind.HTTP_STATUS));
        assertThat(waits).isEmpty();
        server.verify();
    }

    @Test
    void ioErrorIsRetriedAndReportedAsIo() {
        server.expect(times(3), requestTo(SGS_432)).andRespond(request -> {
            throw new IOException("connection reset");
        });

        assertThatThrownBy(this::fetchSelic)
                .isInstanceOfSatisfying(
                        IndicatorFetchException.class,
                        e -> assertThat(e.kind()).isEqualTo(IndicatorFetchException.Kind.IO));
        assertThat(waits).hasSize(2);
    }

    @Test
    void renamedFieldIsBadJson() throws Exception {
        server.expect(once(), requestTo(SGS_433))
                .andRespond(withSuccess(fixture("sgs-renamed-field.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(this::fetchIpca)
                .isInstanceOfSatisfying(
                        IndicatorFetchException.class,
                        e -> assertThat(e.kind()).isEqualTo(IndicatorFetchException.Kind.BAD_JSON));
        assertThat(waits).isEmpty();
        server.verify();
    }

    @Test
    void commaValueIsBadJson() throws Exception {
        server.expect(once(), requestTo(SGS_433))
                .andRespond(withSuccess(fixture("sgs-comma-value.json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(this::fetchIpca)
                .isInstanceOfSatisfying(
                        IndicatorFetchException.class,
                        e -> assertThat(e.kind()).isEqualTo(IndicatorFetchException.Kind.BAD_JSON));
    }

    @Test
    void notJsonIsBadJson() {
        server.expect(once(), requestTo(SGS_432)).andRespond(withSuccess("<html>oops</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(this::fetchSelic)
                .isInstanceOfSatisfying(
                        IndicatorFetchException.class,
                        e -> assertThat(e.kind()).isEqualTo(IndicatorFetchException.Kind.BAD_JSON));
    }

    @Test
    void sourceUrlIsTheRequest() throws Exception {
        server.expect(once(), requestTo(SGS_432))
                .andRespond(withSuccess(fixture("sgs-432-ok.json"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(PTAX_URL))
                .andRespond(withSuccess(fixture("ptax-ok.json"), MediaType.APPLICATION_JSON));

        assertThat(fetchSelic()).extracting(FetchedPoint::sourceUrl).containsOnly(SGS_432);
        assertThat(fetchDollar()).extracting(FetchedPoint::sourceUrl).containsOnly(PTAX_URL);
    }

    @Test
    void exceptionMessageCarriesNeitherUrlNorValue() {
        server.expect(once(), requestTo(SGS_432)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(this::fetchSelic).hasMessage("HTTP_STATUS");
    }
}

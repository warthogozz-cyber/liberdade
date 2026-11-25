package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.markets.internal.GeocodedPlace;
import br.com.liberdadefamilia.markets.internal.Geocoder;
import br.com.liberdadefamilia.markets.internal.GeocodingException;
import br.com.liberdadefamilia.markets.internal.MarketsProperties;
import br.com.liberdadefamilia.markets.internal.NominatimGeocoder;
import br.com.liberdadefamilia.markets.internal.Sleeper;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.client.RestClient;

/**
 * The address search through the real Nominatim client, bound to a mock server: nothing here reaches the network. The
 * sleeper does not sleep, so the polite pace costs no time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, GeocodingIT.Config.class})
class GeocodingIT {

    private static final String BASE = "https://geo.test";
    private static final String CANNED = "[{\"lat\":\"-23.0004\",\"lon\":\"-46.0004\",\"display_name\":\"Rua Exemplo\","
            + "\"address\":{\"suburb\":\"Bairro Exemplo\",\"city\":\"Cidade Exemplo\"}}]";

    /** Counts the calls that get past the cache and the limit, and hands them to the real client. */
    static final class CountingGeocoder implements Geocoder {

        final AtomicInteger calls = new AtomicInteger();
        private final Geocoder delegate;

        CountingGeocoder(Geocoder delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<GeocodedPlace> search(String query) throws GeocodingException {
            calls.incrementAndGet();
            return delegate.search(query);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        Sleeper noSleep() {
            return duration -> {};
        }

        @Bean
        @Primary
        CountingGeocoder countingGeocoder(MarketsProperties properties) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(manyTimes(), requestTo(Matchers.startsWith(BASE + "/search")))
                    .andRespond(withSuccess(CANNED, MediaType.APPLICATION_JSON));
            MarketsProperties.Geocoding geocoding = properties.geocoding();
            return new CountingGeocoder(new NominatimGeocoder(
                    builder,
                    new MarketsProperties(
                            properties.botUrl(),
                            properties.osm(),
                            new MarketsProperties.Geocoding(
                                    true,
                                    "nominatim",
                                    BASE,
                                    geocoding.minInterval(),
                                    geocoding.dailyLimit(),
                                    geocoding.timeout()),
                            properties.instagram())));
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    CountingGeocoder geocoder;

    @MockitoSpyBean
    Plans plans;

    private UUID ana;
    private UUID ws;
    private final List<Runnable> undo = new ArrayList<>();

    @BeforeEach
    void family() {
        doReturn(false).when(plans).legalSetEnabled();
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'markets', true)")
                .param("ws", ws)
                .update();
    }

    @AfterEach
    void restoreLogging() {
        undo.forEach(Runnable::run);
    }

    @Test
    void answersWithRoundedPlaces() {
        MvcTestResult result = geocode("Rua Exemplo 7 answers");

        assertThat(result).hasStatus(200);
        assertThat(result).bodyJson().extractingPath("$.places[0].lat").isEqualTo("-23.000");
        assertThat(result).bodyJson().extractingPath("$.places[0].lon").isEqualTo("-46.000");
        assertThat(result).bodyJson().extractingPath("$.places[0].label").isEqualTo("Bairro Exemplo, Cidade Exemplo");
    }

    @Test
    void twentyFirstIs429() {
        int before = geocoder.calls.get();
        for (int i = 1; i <= 20; i++) {
            assertThat(geocode("limite Rua Exemplo " + i)).hasStatus(200);
        }

        MvcTestResult result = geocode("limite Rua Exemplo 21");

        assertThat(result).hasStatus(429);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.geocoding_limit");
        assertThat(geocoder.calls.get() - before).isEqualTo(20);
    }

    @Test
    void cachedDoesNotCount() {
        int before = geocoder.calls.get();
        for (int i = 0; i < 25; i++) {
            assertThat(geocode("cache Rua Exemplo 5")).hasStatus(200);
        }
        assertThat(geocoder.calls.get() - before).isEqualTo(1);

        // Only that one counted: 19 more different queries pass, the 20th after it does not.
        for (int i = 1; i <= 19; i++) {
            assertThat(geocode("cache outra Rua Exemplo " + i)).hasStatus(200);
        }
        assertThat(geocode("cache outra Rua Exemplo 20")).hasStatus(429);
    }

    @Test
    void theCacheKeyIsTheNormalizedQuery() {
        int before = geocoder.calls.get();

        assertThat(geocode("Normal Rua Exemplo 9")).hasStatus(200);
        assertThat(geocode("  NORMAL, rua   exemplo 9 ")).hasStatus(200);

        assertThat(geocoder.calls.get() - before).isEqualTo(1);
    }

    @Test
    void tooLongIs422() {
        assertThat(geocode("a".repeat(121))).hasStatus(422);
        assertThat(geocode("a".repeat(120) + "   ")).hasStatus(200);
    }

    @Test
    void emptyIs422AndCostsNothing() {
        int before = geocoder.calls.get();

        assertThat(geocode("   ")).hasStatus(422);
        assertThat(geocode("...")).hasStatus(422);

        assertThat(geocoder.calls.get()).isEqualTo(before);
    }

    @Test
    void settingsSayItIsOn() {
        MvcTestResult result = mvc.method(HttpMethod.GET)
                .uri("/v1/workspaces/" + ws + "/markets/settings")
                .with(TestUsers.signedIn(ana, null))
                .exchange();

        assertThat(result).bodyJson().extractingPath("$.geocodingEnabled").isEqualTo(true);
    }

    @Test
    void queryNeverLogged() {
        ListAppender<ILoggingEvent> logs = captureDebugLogs();
        int before = geocoder.calls.get();

        assertThat(geocode("Rua Exemplo 10")).hasStatus(200);

        assertThat(geocoder.calls.get() - before)
                .as("the query went to the service, not to the cache")
                .isOne();
        assertThat(logs.list)
                .as("the debug logs of the web client are on")
                .anyMatch(event -> event.getLoggerName().startsWith("org.springframework.web.client"));
        assertThat(everythingLogged(logs)).noneMatch(line -> {
            String lower = line.toLowerCase(Locale.ROOT);
            return lower.contains("exemplo") || lower.contains("rua%20") || lower.contains("rua+");
        });
    }

    @Test
    void logsCarryNoNames() {
        ListAppender<ILoggingEvent> logs = captureDebugLogs();

        assertThat(geocode("nomes Rua Exemplo 11")).hasStatus(200);

        assertThat(everythingLogged(logs))
                .noneMatch(line -> line.contains("Bairro")
                        || line.contains("Cidade")
                        || line.contains("-23.0")
                        || line.contains("-46.0")
                        || line.contains("Ana Teste"));
    }

    /** DEBUG on the web client and on the markets module (the geocoder's own logger), or the check proves nothing. */
    private ListAppender<ILoggingEvent> captureDebugLogs() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);
        undo.add(() -> root.detachAppender(appender));
        for (String name : List.of("org.springframework.web.client", "br.com.liberdadefamilia.markets")) {
            Logger logger = (Logger) LoggerFactory.getLogger(name);
            Level previous = logger.getLevel();
            logger.setLevel(Level.DEBUG);
            undo.add(() -> logger.setLevel(previous));
        }
        return appender;
    }

    private static List<String> everythingLogged(ListAppender<ILoggingEvent> logs) {
        return logs.list.stream()
                .map(event -> event.getFormattedMessage()
                        + (event.getThrowableProxy() == null
                                ? ""
                                : " " + event.getThrowableProxy().getMessage()))
                .toList();
    }

    private MvcTestResult geocode(String query) {
        var session = TestUsers.signedIn(ana, null);
        return mvc.method(HttpMethod.POST)
                .uri("/v1/workspaces/" + ws + "/markets/geocode")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"" + query + "\"}")
                .exchange();
    }
}

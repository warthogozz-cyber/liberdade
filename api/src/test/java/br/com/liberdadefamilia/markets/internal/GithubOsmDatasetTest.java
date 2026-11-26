package br.com.liberdadefamilia.markets.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.liberdadefamilia.markets.internal.OsmImportException.Reason;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The release download against canned responses; nothing here touches the network. */
class GithubOsmDatasetTest {

    private static final String BASE = "https://data.test/releases/osm-markets-data";
    private static final String BOT = "https://app.test/robo";
    private static final String SHA = "a".repeat(64);
    private static final String MANIFEST = "{\"osmTimestamp\":\"2026-11-23T20:21:30Z\",\"rowCount\":4,\"sha256\":\""
            + SHA + "\",\"attribution\":\"x\",\"license\":\"ODbL-1.0\"}";

    private MockRestServiceServer server;
    private GithubOsmDataset dataset;

    @BeforeEach
    void setUp() {
        dataset = datasetWithMaxBytes(1024);
    }

    private GithubOsmDataset datasetWithMaxBytes(long maxBytes) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MarketsProperties properties = new MarketsProperties(
                BOT,
                new MarketsProperties.Osm("github", BASE, Duration.ofSeconds(5), maxBytes, 0.8, Duration.ofDays(15)),
                null,
                null);
        return new GithubOsmDataset(builder, properties);
    }

    private static Reason reasonOf(ThrowingCallable call) {
        return catchThrowableOfType(OsmImportException.class, call).reason();
    }

    @Test
    void readsTheManifest() throws Exception {
        server.expect(requestTo(BASE + "/manifest.json")).andRespond(withSuccess(MANIFEST, MediaType.APPLICATION_JSON));

        OsmManifest manifest = dataset.manifest();

        assertThat(manifest.osmTimestamp()).isEqualTo(Instant.parse("2026-11-23T20:21:30Z"));
        assertThat(manifest.rowCount()).isEqualTo(4);
        assertThat(manifest.sha256()).isEqualTo(SHA);
        server.verify();
    }

    @Test
    void sendsTheBotUserAgent() throws Exception {
        server.expect(requestTo(BASE + "/manifest.json"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("User-Agent", "LiberdadeBot/1.0 (+" + BOT + ")"))
                .andRespond(withSuccess(MANIFEST, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/markets-br.csv.gz"))
                .andExpect(header("User-Agent", "LiberdadeBot/1.0 (+" + BOT + ")"))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));

        dataset.manifest();
        try (var in = dataset.open()) {
            assertThat(in.readAllBytes()).containsExactly(1, 2, 3);
        }

        server.verify();
    }

    @Test
    void cutsAtMaxBytes() {
        GithubOsmDataset small = datasetWithMaxBytes(10);
        server.expect(requestTo(BASE + "/markets-br.csv.gz"))
                .andRespond(withSuccess(new byte[100], MediaType.APPLICATION_OCTET_STREAM));

        Reason reason = reasonOf(() -> {
            try (var in = small.open()) {
                in.readAllBytes();
            }
        });

        assertThat(reason).isEqualTo(Reason.TOO_LARGE);
    }

    @Test
    void anErrorStatusIsHttp() {
        server.expect(requestTo(BASE + "/manifest.json")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE + "/markets-br.csv.gz")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThat(reasonOf(dataset::manifest)).isEqualTo(Reason.HTTP);
        assertThat(reasonOf(dataset::open)).isEqualTo(Reason.HTTP);
    }

    @Test
    void aSlowServerIsTimeout() {
        server.expect(requestTo(BASE + "/manifest.json"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(requestTo(BASE + "/markets-br.csv.gz"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(reasonOf(dataset::manifest)).isEqualTo(Reason.TIMEOUT);
        assertThat(reasonOf(dataset::open)).isEqualTo(Reason.TIMEOUT);
    }

    @Test
    void aBrokenConnectionIsHttp() {
        server.expect(requestTo(BASE + "/manifest.json"))
                .andRespond(withException(new IOException("connection reset")));

        assertThat(reasonOf(dataset::manifest)).isEqualTo(Reason.HTTP);
    }

    @Test
    void aBadManifestIsRefused() {
        String[] bodies = {
            "not json",
            "[]",
            "{}",
            "{\"osmTimestamp\":\"yesterday\",\"rowCount\":4,\"sha256\":\"" + SHA + "\"}",
            "{\"osmTimestamp\":\"2026-11-23T20:21:30Z\",\"rowCount\":-1,\"sha256\":\"" + SHA + "\"}",
            "{\"osmTimestamp\":\"2026-11-23T20:21:30Z\",\"rowCount\":\"4\",\"sha256\":\"" + SHA + "\"}",
            "{\"osmTimestamp\":\"2026-11-23T20:21:30Z\",\"rowCount\":4,\"sha256\":\"abc\"}",
        };
        for (String body : bodies) {
            server.reset();
            server.expect(requestTo(BASE + "/manifest.json")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            assertThat(reasonOf(dataset::manifest)).as(body).isEqualTo(Reason.BAD_MANIFEST);
        }
    }

    /** A real local server that sends the headers and some of the body, then goes silent. */
    private static HttpServer stallingServer() throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        http.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 1_000_000);
            exchange.getResponseBody().write(new byte[100]);
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        http.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }));
        http.start();
        return http;
    }

    private static GithubOsmDataset realDataset(HttpServer http, Duration timeout) {
        MarketsProperties properties = new MarketsProperties(
                BOT,
                new MarketsProperties.Osm(
                        "github",
                        "http://127.0.0.1:" + http.getAddress().getPort(),
                        timeout,
                        1_000_000L,
                        0.8,
                        Duration.ofDays(15)),
                null,
                null);
        return new GithubOsmDataset(properties);
    }

    private static Reason reasonWithin(Duration limit, Callable<?> call) throws Exception {
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        Future<?> future = pool.submit(call);
        try {
            future.get(limit.toMillis(), TimeUnit.MILLISECONDS);
            throw new AssertionError("expected a failure");
        } catch (java.util.concurrent.ExecutionException e) {
            return ((OsmImportException) e.getCause()).reason();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aBodyThatStallsAfterTheHeadersIsTimeout() throws Exception {
        HttpServer http = stallingServer();
        try {
            GithubOsmDataset real = realDataset(http, Duration.ofSeconds(1));

            Reason reason = reasonWithin(Duration.ofSeconds(15), () -> {
                try (InputStream in = real.open()) {
                    return in.readAllBytes();
                }
            });

            assertThat(reason).isEqualTo(Reason.TIMEOUT);
        } finally {
            http.stop(0);
        }
    }

    @Test
    void aManifestThatStallsAfterTheHeadersIsTimeout() throws Exception {
        HttpServer http = stallingServer();
        try {
            GithubOsmDataset real = realDataset(http, Duration.ofSeconds(1));

            Reason reason = reasonWithin(Duration.ofSeconds(15), real::manifest);

            assertThat(reason).isEqualTo(Reason.TIMEOUT);
        } finally {
            http.stop(0);
        }
    }
}

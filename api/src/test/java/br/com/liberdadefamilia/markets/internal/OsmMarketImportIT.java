package br.com.liberdadefamilia.markets.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.markets.internal.OsmImportException.Reason;
import br.com.liberdadefamilia.markets.internal.OsmMarketImporter.ImportOutcome;
import br.com.liberdadefamilia.support.TestClock;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

/** The weekly import against a real database: swap in one transaction, guards before the swap, alerts as log lines. */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@Import({TestcontainersConfiguration.class, OsmMarketImportIT.Config.class})
class OsmMarketImportIT {

    private static final Instant FIRST = Instant.parse("2026-11-16T20:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-11-23T20:00:00Z");
    private static final String NEW_ROW = "n,5000,SUPERMARKET,Mercado Novo Exemplo,,-10.00000,-50.00000,,,,,,,\n";

    /** A dataset the test controls, the clock the test moves, and a hook the test can hang a check on. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }

        @Bean
        @Primary
        TestDataset testDataset() {
            return new TestDataset();
        }

        @Bean
        TestListener testListener() {
            return new TestListener();
        }
    }

    static class TestDataset implements OsmDataset {
        volatile OsmManifest manifest;
        volatile byte[] gz = new byte[0];
        volatile OsmImportException failure;
        final AtomicInteger opened = new AtomicInteger();

        void serve(Instant timestamp, String csv) {
            gz = OsmTestData.gzip(csv);
            manifest = OsmTestData.manifest(timestamp, csv, gz);
            failure = null;
        }

        @Override
        public OsmManifest manifest() throws OsmImportException {
            if (failure != null) {
                throw failure;
            }
            return manifest;
        }

        @Override
        public java.io.InputStream open() throws OsmImportException {
            opened.incrementAndGet();
            return new java.io.ByteArrayInputStream(gz);
        }
    }

    static class TestListener implements ImportProgressListener {
        volatile Runnable afterDelete = () -> {};

        @Override
        public void afterDelete() {
            afterDelete.run();
        }
    }

    @Autowired
    OsmMarketImporter importer;

    @Autowired
    OsmMarketImportJob job;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    MarketsProperties properties;

    @Autowired
    MarketsAlerts alerts;

    @Autowired
    TestDataset dataset;

    @Autowired
    TestListener listener;

    @Autowired
    TestClock clock;

    @BeforeEach
    void clean() {
        jdbc.sql("delete from osm_markets").update();
        jdbc.sql("delete from osm_market_imports").update();
        listener.afterDelete = () -> {};
        dataset.opened.set(0);
        clock.release();
        dataset.serve(FIRST, OsmTestData.sampleCsv());
    }

    @AfterEach
    void release() {
        clock.release();
    }

    private long markets() {
        return jdbc.sql("select count(*) from osm_markets").query(Long.class).single();
    }

    private long imports() {
        return jdbc.sql("select count(*) from osm_market_imports")
                .query(Long.class)
                .single();
    }

    @Test
    void firstImportFillsTheTable(CapturedOutput output) {
        assertThat(importer.importNow()).isEqualTo(ImportOutcome.IMPORTED);

        assertThat(markets()).isEqualTo(4);
        assertThat(imports()).isEqualTo(1);
        assertThat(jdbc.sql("select name from osm_markets where osm_type = 'n' and osm_id = 1001")
                        .query(String.class)
                        .single())
                .isEqualTo("Mercado Exemplo Um");
        assertThat(jdbc.sql("select count(*) from osm_markets where name is null and instagram is not null")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from osm_markets where brand is null and opening_hours is null")
                        .query(Long.class)
                        .single())
                .isEqualTo(3);
        assertThat(jdbc.sql("select row_count from osm_market_imports")
                        .query(Integer.class)
                        .single())
                .isEqualTo(4);
        assertThat(jdbc.sql("select osm_timestamp from osm_market_imports")
                        .query((rs, n) -> rs.getTimestamp(1).toInstant())
                        .single())
                .isEqualTo(FIRST);
        assertThat(output).contains("MARKETS_IMPORT ok rows=4").doesNotContain("MARKETS_ALERT");
    }

    @Test
    void sameDateIsSkipped(CapturedOutput output) {
        importer.importNow();

        assertThat(importer.importNow()).isEqualTo(ImportOutcome.SKIPPED_SAME_DATE);

        assertThat(markets()).isEqualTo(4);
        assertThat(imports()).isEqualTo(1);
        assertThat(dataset.opened.get()).isEqualTo(1);
        assertThat(output).contains("MARKETS_IMPORT skipped");
    }

    @Test
    void aNewerDateReplacesTheRows() {
        importer.importNow();
        dataset.serve(SECOND, OsmTestData.sampleCsv(3) + NEW_ROW);

        assertThat(importer.importNow()).isEqualTo(ImportOutcome.IMPORTED);

        assertThat(markets()).isEqualTo(4);
        assertThat(imports()).isEqualTo(2);
    }

    @Test
    void shaMismatchIsRefused(CapturedOutput output) {
        importer.importNow();
        dataset.serve(SECOND, OsmTestData.sampleCsv());
        dataset.manifest = new OsmManifest(SECOND, 4, "0".repeat(64));

        assertThat(importer.importNow()).isEqualTo(ImportOutcome.FAILED);

        assertThat(markets()).isEqualTo(4);
        assertThat(imports()).isEqualTo(1);
        assertThat(output).contains("MARKETS_ALERT type=IMPORT_FAILED reason=SHA_MISMATCH");
    }

    @Test
    void halfTheRowsIsRefused(CapturedOutput output) {
        importer.importNow();
        dataset.serve(SECOND, OsmTestData.sampleCsv(2));

        assertThat(importer.importNow()).isEqualTo(ImportOutcome.FAILED);

        assertThat(markets()).isEqualTo(4);
        assertThat(imports()).isEqualTo(1);
        assertThat(output).contains("MARKETS_ALERT type=IMPORT_FAILED reason=TOO_FEW_ROWS");
    }

    @Test
    void manifestCountMismatchIsRefused(CapturedOutput output) {
        importer.importNow();
        dataset.serve(SECOND, OsmTestData.sampleCsv());
        dataset.manifest = new OsmManifest(SECOND, 5, dataset.manifest.sha256());

        assertThat(importer.importNow()).isEqualTo(ImportOutcome.FAILED);

        assertThat(markets()).isEqualTo(4);
        assertThat(imports()).isEqualTo(1);
        assertThat(output).contains("MARKETS_ALERT type=IMPORT_FAILED reason=BAD_MANIFEST");
    }

    @Test
    void theFirstImportSkipsTheRatioCheck() {
        dataset.serve(FIRST, OsmTestData.sampleCsv(1));

        assertThat(importer.importNow()).isEqualTo(ImportOutcome.IMPORTED);

        assertThat(markets()).isEqualTo(1);
    }

    @Test
    void badRowAtTheEndKeepsOldRows(CapturedOutput output) {
        importer.importNow();
        String bad = OsmTestData.sampleCsv() + "n,2000,BAKERY,Padaria Exemplo,,-10.00000,-50.00000,,,,,,,\n";
        dataset.serve(SECOND, bad);

        assertThat(importer.importNow()).isEqualTo(ImportOutcome.FAILED);

        assertThat(markets()).isEqualTo(4);
        assertThat(imports()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from osm_markets where osm_id = 2000")
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(output).contains("MARKETS_ALERT type=IMPORT_FAILED reason=BAD_ROW");
    }

    @Test
    void aRepeatedRowIsBadRow(CapturedOutput output) {
        importer.importNow();
        String repeated = OsmTestData.sampleCsv() + OsmTestData.sampleCsv().split("\n")[1] + "\n";
        dataset.serve(SECOND, repeated);

        assertThat(importer.importNow()).isEqualTo(ImportOutcome.FAILED);

        assertThat(markets()).isEqualTo(4);
        assertThat(output).contains("reason=BAD_ROW");
    }

    @Test
    void datasetFailuresAreAlertsAndNothingChanges(CapturedOutput output) {
        importer.importNow();
        for (Reason reason : new Reason[] {Reason.HTTP, Reason.TIMEOUT, Reason.BAD_MANIFEST, Reason.TOO_LARGE}) {
            dataset.failure = new OsmImportException(reason);

            assertThat(importer.importNow()).isEqualTo(ImportOutcome.FAILED);

            assertThat(output).contains("MARKETS_ALERT type=IMPORT_FAILED reason=" + reason.name());
        }
        assertThat(markets()).isEqualTo(4);
        assertThat(imports()).isEqualTo(1);
    }

    @Test
    void readersSeeOldRowsUntilCommit() {
        importer.importNow();
        dataset.serve(SECOND, OsmTestData.sampleCsv(3) + NEW_ROW);
        AtomicLong seenByAnotherConnection = new AtomicLong(-1);
        listener.afterDelete = () -> seenByAnotherConnection.set(
                CompletableFuture.supplyAsync(this::markets).join());

        assertThat(importer.importNow()).isEqualTo(ImportOutcome.IMPORTED);

        assertThat(seenByAnotherConnection.get()).isEqualTo(4);
        assertThat(markets()).isEqualTo(4);
    }

    @Test
    void staleAfterFifteenDays(CapturedOutput output) {
        Instant now = Instant.parse("2031-03-10T12:00:00Z");
        clock.freeze(now);
        recordImport(now.minus(Duration.ofDays(14)));

        importer.checkStale();
        assertThat(output).doesNotContain("MARKETS_ALERT");

        jdbc.sql("delete from osm_market_imports").update();
        recordImport(now.minus(Duration.ofDays(16)));

        importer.checkStale();
        assertThat(output).contains("MARKETS_ALERT type=DATASET_STALE");
    }

    @Test
    void staleWithoutAnyImportWaitsOneDayOfUptime(CapturedOutput output) {
        clock.freeze(Instant.now().plus(Duration.ofHours(12)));
        importer.checkStale();
        assertThat(output).doesNotContain("MARKETS_ALERT");

        clock.freeze(Instant.now().plus(Duration.ofHours(30)));
        importer.checkStale();
        assertThat(output).contains("MARKETS_ALERT type=DATASET_STALE");
    }

    @Test
    void theScheduledStaleCheckGoesThroughTheJob(CapturedOutput output) {
        clock.freeze(Instant.now().plus(Duration.ofDays(3)));

        job.checkStale();

        assertThat(output).contains("MARKETS_ALERT type=DATASET_STALE");
    }

    @Test
    void startupRunImportsOnlyWhenTheTableIsEmpty() {
        OsmImportStartupRun startup = new OsmImportStartupRun(jdbc, importer);

        startup.importIfEmpty();
        assertThat(markets()).isEqualTo(4);
        assertThat(dataset.opened.get()).isEqualTo(1);

        dataset.serve(SECOND, OsmTestData.sampleCsv(3));
        startup.importIfEmpty();
        assertThat(markets()).isEqualTo(4);
        assertThat(dataset.opened.get()).isEqualTo(1);
    }

    @Test
    void logsCarryNoNames(CapturedOutput output) throws Exception {
        // Debug on for the HTTP client and the importer: a name would leak here if either logged a body or a row.
        Logger web = (Logger) LoggerFactory.getLogger("org.springframework.web.client");
        Logger importerLog = (Logger) LoggerFactory.getLogger(OsmMarketImporter.class);
        Level webBefore = web.getLevel();
        Level importerBefore = importerLog.getLevel();
        web.setLevel(Level.DEBUG);
        importerLog.setLevel(Level.DEBUG);
        try {
            String csv = OsmTestData.sampleCsv();
            byte[] gz = OsmTestData.gzip(csv);
            OsmManifest manifest = OsmTestData.manifest(FIRST, csv, gz);
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            String base = properties.osm().datasetUrl();
            server.expect(requestTo(base + "/manifest.json"))
                    .andRespond(withSuccess(
                            "{\"osmTimestamp\":\"" + FIRST + "\",\"rowCount\":" + manifest.rowCount() + ",\"sha256\":\""
                                    + manifest.sha256() + "\"}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(base + "/markets-br.csv.gz"))
                    .andRespond(withSuccess(gz, MediaType.APPLICATION_OCTET_STREAM));
            OsmMarketImporter real = new OsmMarketImporter(
                    new GithubOsmDataset(builder, properties),
                    jdbcTemplate,
                    transactions,
                    clock,
                    alerts,
                    properties,
                    java.util.Optional.empty());

            assertThat(real.importNow()).isEqualTo(ImportOutcome.IMPORTED);

            server.verify();
            assertThat(output).contains("MARKETS_IMPORT ok rows=4").doesNotContain("Mercado Exemplo");
            assertThat(output).doesNotContain("Sacolao Exemplo").doesNotContain("Feira Exemplo");
        } finally {
            web.setLevel(webBefore);
            importerLog.setLevel(importerBefore);
        }
    }

    private void recordImport(Instant importedAt) {
        jdbc.sql("insert into osm_market_imports (osm_timestamp, row_count, sha256, imported_at) values (?, 4, ?, ?)")
                .param(Timestamp.from(importedAt.minus(Duration.ofDays(1))))
                .param("0".repeat(64))
                .param(Timestamp.from(importedAt))
                .update();
    }
}

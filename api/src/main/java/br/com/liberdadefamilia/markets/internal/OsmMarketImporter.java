package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.internal.OsmImportException.Reason;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Brings the weekly extract into {@code osm_markets} (D14). The file is downloaded and checked (SHA-256) before the
 * database is touched; then one transaction swaps the rows, so readers see the old ones until the commit. Any failure
 * rolls back, keeps the old rows and raises a {@code MARKETS_ALERT}.
 */
@Component
public class OsmMarketImporter {

    public enum ImportOutcome {
        IMPORTED,
        SKIPPED_SAME_DATE,
        FAILED
    }

    private static final int BATCH = 1000;
    private static final Duration NO_IMPORT_GRACE = Duration.ofDays(1);
    private static final String INSERT = """
            INSERT INTO osm_markets (osm_type, osm_id, kind, name, brand, lat, lon, website, instagram,
                                     opening_hours, street, housenumber, suburb, city)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final OsmDataset dataset;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final MarketsAlerts alerts;
    private final MarketsProperties properties;
    private final Optional<ImportProgressListener> listener;
    private final Instant startedAt;

    public OsmMarketImporter(
            OsmDataset dataset,
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock,
            MarketsAlerts alerts,
            MarketsProperties properties,
            Optional<ImportProgressListener> listener) {
        this.dataset = dataset;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.alerts = alerts;
        this.properties = properties;
        this.listener = listener;
        this.startedAt = clock.instant();
    }

    /** One import attempt. Never throws because of the data or the network: those become alerts. */
    public synchronized ImportOutcome importNow() {
        Path file = null;
        try {
            OsmManifest manifest = dataset.manifest();
            Instant last = lastTimestamp();
            if (manifest.osmTimestamp().equals(last)) {
                alerts.importSkipped();
                return ImportOutcome.SKIPPED_SAME_DATE;
            }
            file = Files.createTempFile("osm-markets", ".csv.gz");
            download(file, manifest.sha256());
            int rows = swap(file, manifest);
            alerts.importOk(rows);
            return ImportOutcome.IMPORTED;
        } catch (OsmImportException e) {
            alerts.importFailed(e.reason());
            return ImportOutcome.FAILED;
        } catch (IOException | RuntimeException e) {
            // Nothing from the exception goes to the log: it could carry a path or a row.
            alerts.importFailed(Reason.OTHER);
            return ImportOutcome.FAILED;
        } finally {
            deleteQuietly(file);
        }
    }

    /** Raises {@code DATASET_STALE} when the last import is too old (or there is none after a day of uptime). */
    public void checkStale() {
        Instant now = clock.instant();
        List<Instant> imports = jdbc.query(
                "SELECT imported_at FROM osm_market_imports ORDER BY id DESC LIMIT 1",
                (rs, n) -> rs.getTimestamp(1).toInstant());
        boolean stale = imports.isEmpty()
                ? Duration.between(startedAt, now).compareTo(NO_IMPORT_GRACE) > 0
                : Duration.between(imports.get(0), now)
                                .compareTo(properties.osm().staleAfter())
                        > 0;
        if (stale) {
            alerts.datasetStale();
        }
    }

    private Instant lastTimestamp() {
        List<Instant> last = jdbc.query(
                "SELECT osm_timestamp FROM osm_market_imports ORDER BY id DESC LIMIT 1",
                (rs, n) -> rs.getTimestamp(1).toInstant());
        return last.isEmpty() ? null : last.get(0);
    }

    private void download(Path file, String expectedSha) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = new DigestInputStream(dataset.open(), digest)) {
            Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        if (!HexFormat.of().formatHex(digest.digest()).equals(expectedSha)) {
            throw new OsmImportException(Reason.SHA_MISMATCH);
        }
    }

    private int swap(Path file, OsmManifest manifest) throws OsmImportException {
        try {
            Integer rows = transaction.execute(status -> {
                Long previous = jdbc.queryForObject("SELECT count(*) FROM osm_markets", Long.class);
                jdbc.update("DELETE FROM osm_markets");
                listener.ifPresent(ImportProgressListener::afterDelete);
                int inserted = insertAll(file);
                if (inserted != manifest.rowCount()) {
                    throw new Abort(new OsmImportException(Reason.BAD_MANIFEST));
                }
                if (previous != null
                        && previous > 0
                        && inserted < previous * properties.osm().minRowRatio()) {
                    throw new Abort(new OsmImportException(Reason.TOO_FEW_ROWS));
                }
                jdbc.update(
                        "INSERT INTO osm_market_imports (osm_timestamp, row_count, sha256, imported_at)"
                                + " VALUES (?, ?, ?, ?)",
                        Timestamp.from(manifest.osmTimestamp()),
                        inserted,
                        manifest.sha256(),
                        Timestamp.from(clock.instant()));
                return inserted;
            });
            return rows == null ? 0 : rows;
        } catch (Abort e) {
            throw e.cause;
        } catch (DataIntegrityViolationException e) {
            throw new OsmImportException(Reason.BAD_ROW);
        }
    }

    private int insertAll(Path file) {
        List<Object[]> batch = new ArrayList<>(BATCH);
        try (InputStream in = Files.newInputStream(file)) {
            int rows = OsmCsvReader.read(in, row -> {
                batch.add(args(row));
                if (batch.size() >= BATCH) {
                    flush(batch);
                }
            });
            flush(batch);
            return rows;
        } catch (OsmImportException e) {
            throw new Abort(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void flush(List<Object[]> batch) {
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(INSERT, batch);
            batch.clear();
        }
    }

    private static Object[] args(OsmMarketRow r) {
        return new Object[] {
            String.valueOf(r.type()),
            r.id(),
            r.kind().name(),
            r.name(),
            r.brand(),
            r.lat(),
            r.lon(),
            r.website(),
            r.instagram(),
            r.openingHours(),
            r.street(),
            r.housenumber(),
            r.suburb(),
            r.city()
        };
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // a temp file the system will clean up
            }
        }
    }

    /** Carries a refusal out of the transaction callback, which rolls back. */
    private static final class Abort extends RuntimeException {
        private final transient OsmImportException cause;

        Abort(OsmImportException cause) {
            super(cause.reason().name(), null, false, false);
            this.cause = cause;
        }
    }
}

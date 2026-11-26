package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.internal.OsmImportException.Reason;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The files the weekly workflow publishes as assets of a release of this repository. A polite robot: it says who it
 * is, times out, follows the release's redirect to the file store and cuts a download that is too large. Errors carry
 * the reason, never a URL.
 */
class GithubOsmDataset implements OsmDataset {

    private static final int MANIFEST_MAX_BYTES = 64 * 1024;
    /** The whole file may take this many times the connection timeout, from the request to the last byte. */
    private static final int DOWNLOAD_DEADLINE_FACTOR = 5;

    /** Closes a response whose body stalls: the JDK client only bounds the wait for the headers. */
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "osm-download-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private final RestClient client;
    private final String baseUrl;
    private final long maxBytes;
    private final Duration timeout;

    GithubOsmDataset(MarketsProperties properties) {
        this(RestClient.builder().requestFactory(requestFactory(properties.osm().timeout())), properties);
    }

    /** For tests: the builder is bound to a mock server and keeps its request factory. */
    GithubOsmDataset(RestClient.Builder builder, MarketsProperties properties) {
        this.client = builder.defaultHeader("User-Agent", "LiberdadeBot/1.0 (+" + properties.botUrl() + ")")
                .build();
        this.baseUrl = properties.osm().datasetUrl();
        this.maxBytes = properties.osm().maxBytes();
        this.timeout = properties.osm().timeout();
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration timeout) {
        // The release URL answers with a redirect to the file store.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
        // A backstop for the whole request. The watchdog is what bounds a body that stalls after the headers, and it
        // answers first so the failure is a TIMEOUT.
        factory.setReadTimeout(timeout.multipliedBy(DOWNLOAD_DEADLINE_FACTOR + 1));
        return factory;
    }

    @Override
    public OsmManifest manifest() throws OsmImportException {
        Fetched fetched;
        AtomicBoolean expired = new AtomicBoolean();
        try {
            fetched = client.get().uri(baseUrl + "/manifest.json").exchange((request, response) -> {
                int status = response.getStatusCode().value();
                if (status / 100 != 2) {
                    return new Fetched(status, null);
                }
                ScheduledFuture<?> watch = watch(response, expired, timeout);
                try {
                    byte[] body = response.getBody().readNBytes(MANIFEST_MAX_BYTES + 1);
                    return new Fetched(status, body.length > MANIFEST_MAX_BYTES ? null : body);
                } finally {
                    watch.cancel(false);
                }
            });
        } catch (RestClientException e) {
            throw timedOut(expired) ? new OsmImportException(Reason.TIMEOUT) : failure(e);
        }
        if (expired.get()) {
            Thread.interrupted();
            throw new OsmImportException(Reason.TIMEOUT);
        }
        if (fetched == null || fetched.status() / 100 != 2) {
            throw new OsmImportException(Reason.HTTP);
        }
        if (fetched.body() == null) {
            throw new OsmImportException(Reason.BAD_MANIFEST);
        }
        return OsmManifest.parse(new String(fetched.body(), StandardCharsets.UTF_8));
    }

    @Override
    public InputStream open() throws OsmImportException {
        Opened opened;
        AtomicBoolean expired = new AtomicBoolean();
        try {
            opened = client.get()
                    .uri(baseUrl + "/markets-br.csv.gz")
                    .exchange(
                            (request, response) -> new Opened(
                                    response.getStatusCode().value(),
                                    response,
                                    watch(response, expired, timeout.multipliedBy(DOWNLOAD_DEADLINE_FACTOR))),
                            false);
        } catch (RestClientException e) {
            throw failure(e);
        }
        if (opened == null) {
            throw new OsmImportException(Reason.HTTP);
        }
        if (opened.status() / 100 != 2) {
            opened.watch().cancel(false);
            opened.response().close();
            throw new OsmImportException(Reason.HTTP);
        }
        try {
            return new LimitedStream(opened.response().getBody(), opened.response(), maxBytes, opened.watch(), expired);
        } catch (IOException e) {
            opened.watch().cancel(false);
            opened.response().close();
            throw map(e);
        }
    }

    private static ScheduledFuture<?> watch(ClientHttpResponse response, AtomicBoolean expired, Duration limit) {
        Thread reader = Thread.currentThread();
        return WATCHDOG.schedule(
                () -> {
                    expired.set(true);
                    // Closing alone does not wake a read that waits for bytes; an interrupt does.
                    reader.interrupt();
                    response.close();
                },
                limit.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /** True when the watchdog fired; it also clears the interrupt the watchdog left on this thread. */
    private static boolean timedOut(AtomicBoolean expired) {
        if (expired.get()) {
            Thread.interrupted();
            return true;
        }
        return false;
    }

    private static OsmImportException failure(RestClientException e) {
        return new OsmImportException(isTimeout(e) ? Reason.TIMEOUT : Reason.HTTP);
    }

    private static OsmImportException map(IOException e) {
        return e instanceof OsmImportException known
                ? known
                : new OsmImportException(isTimeout(e) ? Reason.TIMEOUT : Reason.HTTP);
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private record Fetched(int status, byte[] body) {}

    private record Opened(int status, ClientHttpResponse response, ScheduledFuture<?> watch) {}

    /** Counts what passes and raises {@code TOO_LARGE} past the limit; a failing read is raised as its reason. */
    private static final class LimitedStream extends FilterInputStream {

        private final ClientHttpResponse response;
        private final long maxBytes;
        private final ScheduledFuture<?> watch;
        private final AtomicBoolean expired;
        private long count;

        LimitedStream(
                InputStream in,
                ClientHttpResponse response,
                long maxBytes,
                ScheduledFuture<?> watch,
                AtomicBoolean expired) {
            super(in);
            this.response = response;
            this.maxBytes = maxBytes;
            this.watch = watch;
            this.expired = expired;
        }

        private OsmImportException failed(IOException e) {
            return timedOut(expired) ? new OsmImportException(Reason.TIMEOUT) : map(e);
        }

        @Override
        public int read() throws IOException {
            int value;
            try {
                value = super.read();
            } catch (IOException e) {
                throw failed(e);
            }
            if (value >= 0) {
                add(1);
            } else {
                checkNotExpired();
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read;
            try {
                read = super.read(buffer, offset, length);
            } catch (IOException e) {
                throw failed(e);
            }
            if (read > 0) {
                add(read);
            } else if (read < 0) {
                checkNotExpired();
            }
            return read;
        }

        /** Closing the response ends the stream like a normal end of file; an expired deadline must not pass for one. */
        private void checkNotExpired() throws OsmImportException {
            if (timedOut(expired)) {
                throw new OsmImportException(Reason.TIMEOUT);
            }
        }

        private void add(int bytes) throws OsmImportException {
            count += bytes;
            if (count > maxBytes) {
                throw new OsmImportException(Reason.TOO_LARGE);
            }
        }

        @Override
        public void close() {
            watch.cancel(false);
            response.close();
        }
    }
}

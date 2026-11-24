package br.com.liberdadefamilia.indicators.internal;

import br.com.liberdadefamilia.indicators.internal.IndicatorFetchException.Kind;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Banco Central open-data APIs: SGS for the rates and inflation, PTAX (Olinda) for the dollar. A polite robot: it
 * says who it is, times out, and retries only what may pass by itself (timeout, IO error, 5xx). Errors carry the
 * kind, never a URL or a value.
 */
public class BcbIndicatorSource implements IndicatorSource {

    /** The {@code source_ref} of the PTAX series in the V18 seed. */
    static final String PTAX_REF = "CotacaoDolarPeriodo";

    private static final DateTimeFormatter SGS_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter PTAX_DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    // The API sends between one and six fraction digits ("13:06:32.509132", "13:07:26.01516").
    private static final DateTimeFormatter PTAX_QUOTE_TIME = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .toFormatter();

    // Numbers stay exact: the quote is read as a decimal, never as a double.
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private final RestClient client;
    private final IndicatorProperties properties;
    private final Sleeper sleeper;

    public BcbIndicatorSource(IndicatorProperties properties, Sleeper sleeper) {
        this(RestClient.builder().requestFactory(requestFactory(properties.timeout())), properties, sleeper);
    }

    /** For tests: the builder is bound to a mock server and keeps its request factory. */
    public BcbIndicatorSource(RestClient.Builder builder, IndicatorProperties properties, Sleeper sleeper) {
        this.client = builder.defaultHeader("User-Agent", "LiberdadeBot/1.0 (+" + properties.botUrl() + ")")
                .build();
        this.properties = properties;
        this.sleeper = sleeper;
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return factory;
    }

    @Override
    public List<FetchedPoint> fetch(IndicatorCode code, String sourceRef, LocalDate from, LocalDate to)
            throws IndicatorFetchException {
        return PTAX_REF.equals(sourceRef) ? fetchPtax(sourceRef, from, to) : fetchSgs(sourceRef, from, to);
    }

    private List<FetchedPoint> fetchSgs(String ref, LocalDate from, LocalDate to) throws IndicatorFetchException {
        URI uri = URI.create(properties.sgsBaseUrl() + "/dados/serie/bcdata.sgs." + ref + "/dados?formato=json"
                + "&dataInicial=" + SGS_DATE.format(from) + "&dataFinal=" + SGS_DATE.format(to));
        Response response = get(uri);
        if (response.status() == 404) {
            // The SGS answers 404 "Value(s) not found" when the window has no data (recorded): not an error here.
            return List.of();
        }
        JsonNode root = parse(response.body());
        if (!root.isArray()) {
            throw new IndicatorFetchException(Kind.BAD_JSON);
        }
        List<FetchedPoint> points = new ArrayList<>();
        for (JsonNode item : root) {
            JsonNode date = item.get("data");
            JsonNode value = item.get("valor");
            if (date == null || !date.isString() || value == null || !value.isString()) {
                throw new IndicatorFetchException(Kind.BAD_JSON);
            }
            try {
                points.add(new FetchedPoint(
                        LocalDate.parse(date.stringValue(), SGS_DATE),
                        new BigDecimal(value.stringValue()),
                        uri.toString()));
            } catch (DateTimeParseException | NumberFormatException e) {
                throw new IndicatorFetchException(Kind.BAD_JSON);
            }
        }
        points.sort((a, b) -> a.refDate().compareTo(b.refDate()));
        return points;
    }

    private List<FetchedPoint> fetchPtax(String ref, LocalDate from, LocalDate to) throws IndicatorFetchException {
        URI uri = URI.create(properties.ptaxBaseUrl() + "/odata/" + ref
                + "(dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)"
                + "?@dataInicial='" + PTAX_DATE.format(from) + "'&@dataFinalCotacao='" + PTAX_DATE.format(to)
                + "'&$format=json");
        JsonNode root = parse(get(uri).body());
        JsonNode quotes = root.get("value");
        if (quotes == null || !quotes.isArray()) {
            throw new IndicatorFetchException(Kind.BAD_JSON);
        }
        // More than one quote on a day: the latest one is the closing bulletin.
        Map<LocalDate, Quote> byDay = new TreeMap<>();
        for (JsonNode item : quotes) {
            JsonNode sell = item.get("cotacaoVenda");
            JsonNode when = item.get("dataHoraCotacao");
            if (sell == null || !sell.isNumber() || when == null || !when.isString()) {
                throw new IndicatorFetchException(Kind.BAD_JSON);
            }
            LocalDateTime time;
            try {
                time = LocalDateTime.parse(when.stringValue(), PTAX_QUOTE_TIME);
            } catch (DateTimeParseException e) {
                throw new IndicatorFetchException(Kind.BAD_JSON);
            }
            byDay.merge(
                    time.toLocalDate(),
                    new Quote(time, sell.decimalValue()),
                    (old, cur) -> cur.time().isAfter(old.time()) ? cur : old);
        }
        List<FetchedPoint> points = new ArrayList<>();
        byDay.forEach((day, quote) -> points.add(new FetchedPoint(day, quote.value(), uri.toString())));
        return points;
    }

    private static JsonNode parse(String body) throws IndicatorFetchException {
        if (body == null || body.isBlank()) {
            throw new IndicatorFetchException(Kind.BAD_JSON);
        }
        try {
            return MAPPER.readTree(body);
        } catch (JacksonException e) {
            throw new IndicatorFetchException(Kind.BAD_JSON);
        }
    }

    /** The response of a 2xx or a 404; anything else is a failure, retried only when it may pass by itself. */
    private Response get(URI uri) throws IndicatorFetchException {
        List<Duration> waits = properties.retryWaits();
        for (int attempt = 0; ; attempt++) {
            IndicatorFetchException failure;
            boolean retryable;
            try {
                Response response = client.get()
                        .uri(uri)
                        .exchange((req, res) -> new Response(res.getStatusCode().value(), res.bodyTo(String.class)));
                if (response == null) {
                    throw new IndicatorFetchException(Kind.IO);
                }
                int status = response.status();
                if (status / 100 == 2 || status == 404) {
                    return response;
                }
                failure = new IndicatorFetchException(Kind.HTTP_STATUS);
                retryable = status / 100 == 5;
            } catch (ResourceAccessException e) {
                failure = new IndicatorFetchException(isTimeout(e) ? Kind.TIMEOUT : Kind.IO);
                retryable = true;
            } catch (RestClientException e) {
                failure = new IndicatorFetchException(Kind.IO);
                retryable = true;
            }
            if (!retryable || attempt >= waits.size()) {
                throw failure;
            }
            try {
                sleeper.sleep(waits.get(attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IndicatorFetchException(Kind.IO);
            }
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private record Response(int status, String body) {}

    private record Quote(LocalDateTime time, BigDecimal value) {}
}

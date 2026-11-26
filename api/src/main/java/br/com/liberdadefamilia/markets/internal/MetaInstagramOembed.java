package br.com.liberdadefamilia.markets.internal;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The oEmbed of Instagram on the Graph API. It sends no access token (public posts need none), asks for the plain
 * blockquote without the script (the app never runs the script of Instagram) and says who it is. A 400 is a refusal,
 * not a failure: profiles and missing posts answer it.
 */
public class MetaInstagramOembed implements InstagramOembed {

    static final String BLOCKQUOTE = "<blockquote class=\"instagram-media\"";

    private static final String OEMBED = "/instagram_oembed?url={url}&maxwidth=320&omitscript=true";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RestClient client;
    private final String baseUrl;

    public MetaInstagramOembed(MarketsProperties properties) {
        this(
                RestClient.builder()
                        .requestFactory(requestFactory(properties.instagram().timeout())),
                properties);
    }

    /** For tests: the builder is bound to a mock server and keeps its request factory. */
    public MetaInstagramOembed(RestClient.Builder builder, MarketsProperties properties) {
        this.client = builder.defaultHeader(HttpHeaders.USER_AGENT, "LiberdadeBot/1.0 (+" + properties.botUrl() + ")")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.baseUrl = properties.instagram().baseUrl();
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return factory;
    }

    @Override
    public Optional<String> html(String canonicalUrl) throws OembedException {
        String body;
        try {
            body = client.get()
                    .uri(baseUrl + OEMBED, canonicalUrl)
                    .retrieve()
                    .onStatus(MetaInstagramOembed::isRefusal, (request, response) -> {
                        throw new Refusal();
                    })
                    .body(String.class);
        } catch (Refusal e) {
            return Optional.empty();
        } catch (RestClientException e) {
            // The exception text carries the address that was asked: keep none of it.
            throw new OembedException("the embed service failed");
        }
        return parse(body);
    }

    /** Only 400 and 404 say "not embeddable"; any other 4xx (401, 403, 429) is a failure, so it is not kept. */
    private static boolean isRefusal(HttpStatusCode status) {
        return status.value() == 400 || status.value() == 404;
    }

    private static Optional<String> parse(String body) throws OembedException {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (JacksonException e) {
            throw new OembedException("the embed service sent a bad answer");
        }
        JsonNode html = root.get("html");
        if (html == null || !html.isString()) {
            return Optional.empty();
        }
        String text = html.stringValue().strip();
        return text.startsWith(BLOCKQUOTE) && !text.toLowerCase(Locale.ROOT).contains("<script")
                ? Optional.of(text)
                : Optional.empty();
    }

    /** Thrown inside the status handler and caught right away: a refusal is an empty answer. */
    private static final class Refusal extends RuntimeException {
        Refusal() {
            super(null, null, false, false);
        }
    }
}

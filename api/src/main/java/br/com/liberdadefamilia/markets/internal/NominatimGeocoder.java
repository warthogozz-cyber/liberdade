package br.com.liberdadefamilia.markets.internal;

import br.com.liberdadefamilia.markets.domain.GeoPoint;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Nominatim, the OpenStreetMap address search (D9). A polite robot: it says who it is, times out, asks for at most 5
 * results in Brazil and rounds every point to 3 decimals. Errors say what went wrong, never what was asked: the query
 * is not in any message, and this class logs nothing.
 */
public class NominatimGeocoder implements Geocoder {

    static final int POINT_DECIMALS = 3;
    /** The longest label the family's address takes (D9). */
    static final int LABEL_MAX = 40;

    private static final String SEARCH = "/search?q={query}&format=jsonv2&addressdetails=1&limit=5&countrycodes=br";
    private static final List<String> DISTRICT_KEYS = List.of("suburb", "neighbourhood", "quarter", "city_district");
    private static final List<String> CITY_KEYS = List.of("city", "town", "village", "municipality");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RestClient client;
    private final String baseUrl;

    public NominatimGeocoder(MarketsProperties properties) {
        this(
                RestClient.builder()
                        .requestFactory(requestFactory(properties.geocoding().timeout())),
                properties);
    }

    /** For tests: the builder is bound to a mock server and keeps its request factory. */
    public NominatimGeocoder(RestClient.Builder builder, MarketsProperties properties) {
        this.client = builder.defaultHeader(HttpHeaders.USER_AGENT, "LiberdadeBot/1.0 (+" + properties.botUrl() + ")")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.baseUrl = properties.geocoding().baseUrl();
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return factory;
    }

    @Override
    public List<GeocodedPlace> search(String query) throws GeocodingException {
        String body;
        try {
            body = client.get().uri(baseUrl + SEARCH, query).retrieve().body(String.class);
        } catch (RestClientException e) {
            // The exception text may carry the address, with the query in it: keep none of it.
            throw new GeocodingException("the address service failed");
        }
        return parse(body);
    }

    private static List<GeocodedPlace> parse(String body) throws GeocodingException {
        if (body == null || body.isBlank()) {
            throw new GeocodingException("the address service sent nothing");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (JacksonException e) {
            throw new GeocodingException("the address service sent a bad answer");
        }
        if (!root.isArray()) {
            throw new GeocodingException("the address service sent a bad answer");
        }
        List<GeocodedPlace> places = new ArrayList<>();
        for (JsonNode item : root) {
            places.add(place(item));
        }
        return places;
    }

    private static GeocodedPlace place(JsonNode item) throws GeocodingException {
        JsonNode lat = item.get("lat");
        JsonNode lon = item.get("lon");
        if (lat == null || !lat.isString() || lon == null || !lon.isString()) {
            throw new GeocodingException("the address service sent a bad answer");
        }
        GeoPoint point;
        try {
            point = new GeoPoint(new BigDecimal(lat.stringValue()), new BigDecimal(lon.stringValue()))
                    .rounded(POINT_DECIMALS);
        } catch (IllegalArgumentException e) { // not a number, or off the globe
            throw new GeocodingException("the address service sent a bad answer");
        }
        String label = label(item);
        if (label == null) {
            throw new GeocodingException("the address service sent a place without a name");
        }
        return new GeocodedPlace(point, label);
    }

    /** District and city, or city and state; the first words of the full name when the address has neither. */
    private static String label(JsonNode item) {
        JsonNode address = item.get("address");
        String district = first(address, DISTRICT_KEYS);
        String city = first(address, CITY_KEYS);
        String state = first(address, List.of("state"));
        String label;
        if (district != null && city != null) {
            label = district + ", " + city;
        } else if (city != null && state != null) {
            label = city + ", " + state;
        } else if (city != null) {
            label = city;
        } else if (district != null) {
            label = district;
        } else {
            label = firstWords(item.get("display_name"));
        }
        return label == null ? null : cut(label);
    }

    private static String first(JsonNode address, List<String> keys) {
        if (address == null || !address.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = address.get(key);
            if (value != null && value.isString() && !value.stringValue().isBlank()) {
                return value.stringValue().strip();
            }
        }
        return null;
    }

    private static String firstWords(JsonNode displayName) {
        if (displayName == null
                || !displayName.isString()
                || displayName.stringValue().isBlank()) {
            return null;
        }
        String[] parts = displayName.stringValue().split(",");
        return parts.length > 1 ? (parts[0].strip() + ", " + parts[1].strip()) : parts[0].strip();
    }

    private static String cut(String label) {
        if (label.length() <= LABEL_MAX) {
            return label;
        }
        int end = Character.isHighSurrogate(label.charAt(LABEL_MAX - 1)) ? LABEL_MAX - 1 : LABEL_MAX;
        return label.substring(0, end).strip();
    }
}

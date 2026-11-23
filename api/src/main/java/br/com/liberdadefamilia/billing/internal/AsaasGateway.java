package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.domain.FounderOffer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Asaas over HTTP. Construction never needs the API key: a missing key only matters when a method is called. Errors
 * carry the status, never the response body.
 */
@Component
@ConditionalOnProperty(name = "liberdade.billing.gateway", havingValue = "asaas", matchIfMissing = true)
class AsaasGateway implements PaymentGateway {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private static final Set<String> REFUNDED = Set.of("REFUNDED", "REFUND_REQUESTED", "REFUND_IN_PROGRESS");
    private static final Logger log = LoggerFactory.getLogger(AsaasGateway.class);
    private static final String LOGO_BASE64 = loadLogo();

    private final RestClient client;

    @Autowired
    AsaasGateway(BillingProperties properties) {
        this(RestClient.builder().requestFactory(requestFactory()), properties);
    }

    /** For tests: the builder is bound to a mock server and keeps its request factory. */
    AsaasGateway(RestClient.Builder builder, BillingProperties properties) {
        this.client = builder.baseUrl(properties.asaas().baseUrl())
                .defaultHeader("access_token", properties.asaas().apiKey())
                .defaultHeader("User-Agent", "liberdade-api")
                .defaultStatusHandler(status -> !status.is2xxSuccessful(), (req, res) -> {
                    throw new PaymentGatewayException(res.getStatusCode().value());
                })
                .build();
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    private static String loadLogo() {
        try {
            return Base64.getEncoder()
                    .encodeToString(new ClassPathResource("billing/checkout-logo.png").getContentAsByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        String callbackUrl = request.callbackUrl();
        Map<String, Object> item = Map.of(
                "name",
                FounderOffer.NAME,
                "description",
                FounderOffer.DESCRIPTION,
                "quantity",
                1,
                "value",
                FounderOffer.PRICE.amount(),
                "imageBase64",
                LOGO_BASE64);
        Map<String, Object> body = Map.of(
                "billingTypes", List.of("PIX", "CREDIT_CARD"),
                "chargeTypes", List.of("DETACHED"),
                "minutesToExpire", 60,
                "externalReference", request.orderId().toString(),
                "items", List.of(item),
                "callback", Map.of("successUrl", callbackUrl, "cancelUrl", callbackUrl, "expiredUrl", callbackUrl));
        JsonNode response = exchange(
                () -> client.post().uri("/v3/checkouts").body(body).retrieve().body(JsonNode.class));
        String id = text(response, "id");
        String link = text(response, "link");
        if (id.isBlank() || link.isBlank()) {
            throw new PaymentGatewayException(0);
        }
        try {
            return new CheckoutSession(id, URI.create(link));
        } catch (IllegalArgumentException e) {
            throw new PaymentGatewayException(0);
        }
    }

    /**
     * The payment that counts for the checkout. A checkout can hold several attempts (a refused card, then a Pix), so a
     * paid entry wins, then a refunded one, and only then the first. The payment date is not read: Asaas gives only the
     * day, and a day is not a moment.
     */
    @Override
    public Optional<PaymentInfo> findPayment(String checkoutId) {
        JsonNode response = exchange(() -> client.get()
                .uri("/v3/payments?checkoutSession={id}", checkoutId)
                .retrieve()
                .body(JsonNode.class));
        List<PaymentInfo> payments = new ArrayList<>();
        for (JsonNode entry : response.path("data")) {
            if (entry.isObject()) {
                payments.add(new PaymentInfo(text(entry, "id"), text(entry, "status")));
            }
        }
        return payments.stream()
                .filter(OrderPayments::confirmed)
                .findFirst()
                .or(() -> payments.stream()
                        .filter(payment -> REFUNDED.contains(payment.status()))
                        .findFirst())
                .or(() -> payments.stream().findFirst());
    }

    @Override
    public void refund(String paymentId) {
        exchange(() -> client.post()
                .uri("/v3/payments/{id}/refund", paymentId)
                .body(Map.of())
                .retrieve()
                .toBodilessEntity());
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asString("");
    }

    /**
     * Timeouts and IO errors have no status: they become {@code PaymentGatewayException(0)}, without their text. Only
     * the class of the cause is logged, so an operator can tell a timeout from a refused connection.
     */
    private static <T> T exchange(Supplier<T> call) {
        try {
            T result = call.get();
            if (result == null) {
                throw new PaymentGatewayException(0);
            }
            return result;
        } catch (RestClientException e) {
            Throwable cause = e.getMostSpecificCause();
            log.warn("asaas call failed: {}", cause.getClass().getName());
            throw new PaymentGatewayException(0);
        }
    }
}

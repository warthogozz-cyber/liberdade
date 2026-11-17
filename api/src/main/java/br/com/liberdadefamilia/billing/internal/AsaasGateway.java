package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.billing.domain.FounderOffer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
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

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final BigDecimal PRICE = BigDecimal.valueOf(FounderOffer.PRICE_CENTS, 2);
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
                PRICE,
                "imageBase64",
                LOGO_BASE64);
        Map<String, Object> body = Map.of(
                "billingTypes", List.of("PIX", "CREDIT_CARD"),
                "chargeTypes", List.of("DETACHED"),
                "minutesToExpire", 60,
                "externalReference", request.orderId().toString(),
                "items", List.of(item),
                "callback", Map.of("successUrl", callbackUrl, "cancelUrl", callbackUrl, "expiredUrl", callbackUrl));
        JsonNode response = exchange(() -> client.post()
                .uri("/v3/checkouts")
                .body(body)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), (req, res) -> {
                    throw new PaymentGatewayException(res.getStatusCode().value());
                })
                .body(JsonNode.class));
        return new CheckoutSession(
                response.path("id").asString(), URI.create(response.path("link").asString()));
    }

    @Override
    public Optional<PaymentInfo> findPayment(String checkoutId) {
        JsonNode response = exchange(() -> client.get()
                .uri(uri -> uri.path("/v3/payments")
                        .queryParam("checkoutSession", checkoutId)
                        .build())
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), (req, res) -> {
                    throw new PaymentGatewayException(res.getStatusCode().value());
                })
                .body(JsonNode.class));
        JsonNode first = response.path("data").path(0);
        if (first.isMissingNode() || first.isNull()) {
            return Optional.empty();
        }
        String date = first.path("paymentDate").asString("");
        return Optional.of(new PaymentInfo(
                first.path("id").asString(),
                first.path("status").asString(),
                date.isEmpty()
                        ? null
                        : LocalDate.parse(date).atStartOfDay(SAO_PAULO).toInstant()));
    }

    @Override
    public void refund(String paymentId) {
        exchange(() -> client.post()
                .uri("/v3/payments/{id}/refund", paymentId)
                .body(Map.of())
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), (req, res) -> {
                    throw new PaymentGatewayException(res.getStatusCode().value());
                })
                .toBodilessEntity());
    }

    /** Timeouts and IO errors have no status: they become {@code PaymentGatewayException(0)}, without their text. */
    private static <T> T exchange(Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException e) {
            throw new PaymentGatewayException(0);
        }
    }
}

package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.liberdadefamilia.shared.RateLimiter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

/** The webhook's checks that the HTTP layer cannot reach: a body without a declared length and odd token lengths. */
class WebhookControllerTest {

    private static final String EVENT = "{\"id\":\"evt_1\",\"event\":\"PAYMENT_CREATED\"}";

    private final WebhookService service = mock(WebhookService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), ZoneOffset.UTC);

    private WebhookController controller(String token) {
        BillingProperties properties = new BillingProperties(
                true, true, "fake", List.of("checkout.e2e.test"), token, new BillingProperties.Asaas("", ""));
        return new WebhookController(
                service,
                new BillingStatus(properties),
                properties,
                new RateLimiter(clock, 120, Duration.ofMinutes(1)),
                new RateLimiter(clock, 20, Duration.ofMinutes(1)),
                JsonMapper.builder().build());
    }

    /** A chunked request: the container does not know the length, so only the bytes read can tell. */
    private static MockHttpServletRequest chunked(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/billing/webhooks/asaas") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContent(body);
        return request;
    }

    @Test
    void anUndeclaredBodyOver64KbIs413() {
        String token = "t".repeat(40);
        byte[] big = new byte[WebhookController.MAX_BODY_BYTES + 1];

        assertThatThrownBy(() -> controller(token).receive(chunked(big), token))
                .isInstanceOfSatisfying(
                        BillingException.class, e -> assertThat(e.status()).isEqualTo(413));
        verify(service, never()).handle(any());
    }

    @Test
    void anUndeclaredBodyOfExactly64KbIsRead() {
        String token = "t".repeat(40);
        byte[] padded = new byte[WebhookController.MAX_BODY_BYTES];
        Arrays.fill(padded, (byte) ' ');
        byte[] event = EVENT.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(event, 0, padded, 0, event.length);

        assertThatCode(() -> controller(token).receive(chunked(padded), token)).doesNotThrowAnyException();
        verify(service).handle(any());
    }

    @ParameterizedTest(name = "a token of {0} characters never authenticates")
    @ValueSource(ints = {31, 256})
    void aConfiguredTokenOfTheWrongLengthNeverAuthenticates(int length) {
        String token = "t".repeat(length);

        assertThatThrownBy(() -> controller(token).receive(chunked(EVENT.getBytes(StandardCharsets.UTF_8)), token))
                .isInstanceOfSatisfying(
                        BillingException.class, e -> assertThat(e.status()).isEqualTo(401));
        verify(service, never()).handle(any());
    }
}

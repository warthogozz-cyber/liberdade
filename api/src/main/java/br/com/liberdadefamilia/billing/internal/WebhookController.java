package br.com.liberdadefamilia.billing.internal;

import br.com.liberdadefamilia.shared.RateLimitedException;
import br.com.liberdadefamilia.shared.RateLimiter;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Where Asaas tells us about payments. Public and without a session: the token in the header is the only check. */
@Hidden
@RestController
class WebhookController {

    static final int MAX_BODY_BYTES = 65_536;
    private static final String RATE_KEY = "asaas";

    private final WebhookService service;
    private final BillingStatus status;
    private final BillingProperties properties;
    private final RateLimiter limiter;
    private final JsonMapper json;

    WebhookController(
            WebhookService service,
            BillingStatus status,
            BillingProperties properties,
            @Qualifier("webhookRateLimiter") RateLimiter limiter,
            JsonMapper json) {
        this.service = service;
        this.status = status;
        this.properties = properties;
        this.limiter = limiter;
        this.json = json;
    }

    @PostMapping("/v1/billing/webhooks/asaas")
    @ResponseStatus(HttpStatus.OK)
    void receive(
            HttpServletRequest request,
            @RequestHeader(name = "asaas-access-token", required = false) @Nullable String token)
            throws IOException {
        if (!limiter.tryAcquire(RATE_KEY)) {
            throw new RateLimitedException(limiter.retryAfterSeconds(RATE_KEY));
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            throw BillingException.requestTooLarge();
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw BillingException.requestTooLarge();
        }
        if (!authentic(token)) {
            throw BillingException.webhookUnauthorized();
        }
        AsaasEvent event;
        try {
            event = json.readValue(body, AsaasEvent.class);
        } catch (JacksonException failure) {
            throw BillingException.webhookInvalid();
        }
        if (event == null || isBlank(event.id()) || isBlank(event.event())) {
            throw BillingException.webhookInvalid();
        }
        service.handle(event);
    }

    private boolean authentic(@Nullable String given) {
        if (!status.webhookTokenValid()) {
            return false;
        }
        byte[] expected = properties.webhookToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = (given == null ? "" : given).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}

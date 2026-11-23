package br.com.liberdadefamilia.billing.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Whether selling can happen, worked out from the configuration without ever failing the startup (D5). */
@Component
@EnableConfigurationProperties(BillingProperties.class)
public class BillingStatus {

    static final int MIN_TOKEN_LENGTH = 32;
    static final int MAX_TOKEN_LENGTH = 255;

    private final BillingProperties properties;

    BillingStatus(BillingProperties properties) {
        this.properties = properties;
    }

    /** Selling is on and either the fake gateway is in use or the Asaas key and webhook token are in place. */
    public boolean configured() {
        if (!properties.enabled()) {
            return false;
        }
        return properties.fakeGateway() || (!properties.asaas().apiKey().isBlank() && webhookTokenValid());
    }

    /** The doors are open: configured and the pre-sale switched on (D6). */
    public boolean presaleOpen() {
        return configured() && properties.presaleOpen();
    }

    /** A webhook token of 32 to 255 characters; an empty one never authenticates. */
    public boolean webhookTokenValid() {
        int length = properties.webhookToken().length();
        return length >= MIN_TOKEN_LENGTH && length <= MAX_TOKEN_LENGTH;
    }
}

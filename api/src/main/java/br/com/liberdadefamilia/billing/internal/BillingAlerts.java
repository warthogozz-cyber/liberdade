package br.com.liberdadefamilia.billing.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Tells the person watching the logs that something needs a human. The line carries the type and nothing else. */
@Component
class BillingAlerts {

    private static final Logger log = LoggerFactory.getLogger("br.com.liberdadefamilia.billing.alerts");

    void raise(AlertType type) {
        log.warn("BILLING_ALERT type={}", type);
    }
}

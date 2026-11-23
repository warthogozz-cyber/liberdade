package br.com.liberdadefamilia.billing.domain;

import br.com.liberdadefamilia.shared.Money;
import java.time.Instant;
import java.time.ZoneOffset;

/** The one thing on sale. The price is code, not configuration: changing it is a product decision. */
public final class FounderOffer {

    public static final String PRODUCT = "FOUNDER_2026";
    public static final Money PRICE = Money.brl("89.00");
    /** The price in integer cents, as the order rows store it. */
    public static final long PRICE_CENTS = PRICE.amount().movePointRight(2).longValueExact();

    public static final String CURRENCY = PRICE.currency().getCurrencyCode();
    public static final String NAME = "Família Fundadora";
    public static final String DESCRIPTION = "Plano Família por 1 ano";

    private FounderOffer() {}

    /** One calendar year after the payment, in UTC. */
    public static Instant endsAt(Instant paidAt) {
        return paidAt.atOffset(ZoneOffset.UTC).plusYears(1).toInstant();
    }
}

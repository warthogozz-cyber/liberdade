package br.com.liberdadefamilia.shared;

import java.util.List;

/** The payment methods a country offers, in the order the screens list them. */
public final class PaymentMethods {

    private static final List<PaymentMethod> BRAZIL = List.of(
            PaymentMethod.PIX,
            PaymentMethod.CREDIT_CARD,
            PaymentMethod.DEBIT_CARD,
            PaymentMethod.BOLETO,
            PaymentMethod.BANK_TRANSFER,
            PaymentMethod.CASH);

    private PaymentMethods() {}

    /**
     * The methods of the country, an ISO 3166 alpha-2 code.
     *
     * @throws IllegalArgumentException when the country is not supported
     */
    public static List<PaymentMethod> forCountry(String country) {
        if ("BR".equals(country)) {
            return BRAZIL;
        }
        throw new IllegalArgumentException("Unsupported country");
    }
}

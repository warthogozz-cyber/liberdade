package br.com.liberdadefamilia.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PaymentMethodsTest {

    @Test
    void brazilHasSixInOrder() {
        assertThat(PaymentMethods.forCountry("BR"))
                .containsExactly(
                        PaymentMethod.PIX,
                        PaymentMethod.CREDIT_CARD,
                        PaymentMethod.DEBIT_CARD,
                        PaymentMethod.BOLETO,
                        PaymentMethod.BANK_TRANSFER,
                        PaymentMethod.CASH);
    }

    @Test
    void unknownCountryFails() {
        assertThatThrownBy(() -> PaymentMethods.forCountry("US")).isInstanceOf(IllegalArgumentException.class);
    }
}

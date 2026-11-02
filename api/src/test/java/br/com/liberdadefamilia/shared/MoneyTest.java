package br.com.liberdadefamilia.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    private static final Currency BRL = Currency.getInstance("BRL");
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void sumsAccountBalanceFromPrd71() {
        var balance = Money.brl("1000")
                .plus(Money.brl("5000"))
                .minus(Money.brl("1234.56"))
                .minus(Money.brl("500"));
        assertThat(balance).isEqualTo(Money.brl("4265.44"));
        assertThat(balance.plus(Money.brl("500"))).isEqualTo(Money.brl("4765.44"));
    }

    @Test
    void equalityIgnoresTrailingZeros() {
        assertThat(Money.brl("10")).isEqualTo(Money.brl("10.00")).hasSameHashCodeAs(Money.brl("10.00"));
    }

    @Test
    void allocatesWithLargestRemainderFromPrd72() {
        assertThat(Money.brl("100").allocate(3))
                .containsExactly(Money.brl("33.34"), Money.brl("33.33"), Money.brl("33.33"));
    }

    @Test
    void acceptsTrailingZerosBeyondTheFourthDecimal() {
        assertThatCode(() -> Money.of(new BigDecimal("1.23450"), BRL)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMoreThanFourDecimals() {
        assertThatThrownBy(() -> Money.brl("0.00001")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsLargestValueOfNumeric19x4() {
        assertThatCode(() -> Money.brl("999999999999999.9999")).doesNotThrowAnyException();
    }

    @Test
    void rejectsValueAtTenToThe15() {
        assertThatThrownBy(() -> Money.brl("1000000000000000")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsScientificNotationValueAboveLimit() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("1E+15"), BRL)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e3", "+5", " 5", "1.234,56", "", "abc"})
    void parseRejectsNonPlainDecimalText(String text) {
        assertThatThrownBy(() -> Money.brl(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void plusRejectsDifferentCurrencies() {
        assertThatThrownBy(() -> Money.brl("1").plus(Money.of(BigDecimal.ONE, USD)))
                .isInstanceOfSatisfying(CurrencyMismatchException.class, e -> {
                    assertThat(e.code()).isEqualTo("money.currency-mismatch");
                    assertThat(e.status()).isEqualTo(422);
                    assertThat(e.detail()).isEqualTo("As moedas dos valores não são iguais.");
                    assertThat(e).hasMessage("money.currency-mismatch");
                });
    }

    @Test
    void minusRejectsDifferentCurrencies() {
        assertThatThrownBy(() -> Money.brl("1").minus(Money.of(BigDecimal.ONE, USD)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void compareToRejectsDifferentCurrencies() {
        assertThatThrownBy(() -> Money.brl("1").compareTo(Money.of(BigDecimal.ONE, USD)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void compareToOrdersSameCurrencyValues() {
        assertThat(Money.brl("1")).isLessThan(Money.brl("2"));
    }

    @Test
    void rejectsCurrencyWithoutDefinedFractionDigits() {
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, Currency.getInstance("XXX")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullFields() {
        assertThatThrownBy(() -> Money.of(null, BRL)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @CsvSource({"2.345,2.34", "2.355,2.36", "-0.005,0.00", "1234.5,1234.50", "100,100.00"})
    void toPlainStringRoundsHalfEvenAtCurrencyScale(String input, String expected) {
        assertThat(Money.brl(input).toPlainString()).isEqualTo(expected);
    }

    @Test
    void allocatesNegativeAmount() {
        assertThat(Money.brl("-100").allocate(3))
                .containsExactly(Money.brl("-33.34"), Money.brl("-33.33"), Money.brl("-33.33"));
    }

    @Test
    void allocatesTwoCentsInThreeParts() {
        assertThat(Money.brl("0.02").allocate(3))
                .containsExactly(Money.brl("0.01"), Money.brl("0.01"), Money.brl("0.00"));
    }

    @Test
    void allocateRejectsFractionOfCent() {
        assertThatThrownBy(() -> Money.brl("0.005").allocate(2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allocateRejectsZeroParts() {
        assertThatThrownBy(() -> Money.brl("1").allocate(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negateFlipsSign() {
        assertThat(Money.brl("5").negate()).isEqualTo(Money.brl("-5"));
    }

    @Test
    void zeroIsNotPositiveAndOneCentIs() {
        assertThat(Money.brl("0").isPositive()).isFalse();
        assertThat(Money.brl("0.01").isPositive()).isTrue();
        assertThat(Money.zero(BRL)).isEqualTo(Money.brl("0.00"));
    }

    @Test
    void exceptionMessagesNeverContainAmounts() {
        assertThatThrownBy(() -> Money.brl("123456.00001"))
                .isInstanceOf(IllegalArgumentException.class)
                .message()
                .doesNotContain("123456");
        assertThatThrownBy(() -> Money.of(new BigDecimal("1234560000000000"), BRL))
                .isInstanceOf(IllegalArgumentException.class)
                .message()
                .doesNotContain("123456");
    }
}

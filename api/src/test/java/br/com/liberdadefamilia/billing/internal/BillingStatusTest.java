package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BillingStatusTest {

    private static BillingStatus status(boolean enabled, boolean presale, String gateway, String key, int tokenLength) {
        return new BillingStatus(new BillingProperties(
                enabled,
                presale,
                gateway,
                List.of("checkout.e2e.test"),
                "t".repeat(tokenLength),
                new BillingProperties.Asaas("https://api-sandbox.asaas.com", key)));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
                    disabled fake           | false | true  | fake  |     | 40  | false | false
                    fake enabled            | true  | true  | fake  |     | 0   | true  | true
                    fake enabled no presale | true  | false | fake  |     | 0   | true  | false
                    asaas without key       | true  | true  | asaas |     | 40  | false | false
                    asaas token 31          | true  | true  | asaas | key | 31  | false | false
                    asaas token 32          | true  | true  | asaas | key | 32  | true  | true
                    asaas token 255         | true  | true  | asaas | key | 255 | true  | true
                    asaas token 256         | true  | true  | asaas | key | 256 | false | false
                    asaas empty token       | true  | true  | asaas | key | 0   | false | false
                    asaas ok no presale     | true  | false | asaas | key | 40  | true  | false
                    """)
    void configuredAndPresaleOpen(
            String name,
            boolean enabled,
            boolean presale,
            String gateway,
            String key,
            int tokenLength,
            boolean configured,
            boolean presaleOpen) {
        BillingStatus status = status(enabled, presale, gateway, key == null ? "" : key, tokenLength);

        assertThat(status.configured()).isEqualTo(configured);
        assertThat(status.presaleOpen()).isEqualTo(presaleOpen);
    }

    @ParameterizedTest
    @CsvSource({"0,false", "31,false", "32,true", "255,true", "256,false"})
    void webhookTokenNeedsThirtyTwoToTwoHundredFiftyFiveCharacters(int length, boolean valid) {
        assertThat(status(true, true, "fake", "", length).webhookTokenValid()).isEqualTo(valid);
    }
}

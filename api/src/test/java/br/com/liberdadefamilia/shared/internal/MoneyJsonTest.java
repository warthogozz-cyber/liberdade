package br.com.liberdadefamilia.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.shared.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@Import({ProblemHandler.class, MoneyJsonTest.MoneyEchoController.class})
class MoneyJsonTest {

    @Autowired
    MockMvcTester mvc;

    private MockMvcTester.MockMvcRequestBuilder post(String body) {
        return mvc.post()
                .uri("/test/money")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private void assertMalformed(String body) {
        var result = post(body).exchange();
        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.malformed");
    }

    @Test
    void echoesNormalizedAmount() {
        var result = post("{\"amount\":\"1234.5\",\"currency\":\"BRL\"}").exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyText().isEqualTo("{\"amount\":\"1234.50\",\"currency\":\"BRL\"}");
    }

    @Test
    void acceptsFieldsInAnyOrderAndIgnoresUnknownFields() {
        var result = post("{\"extra\":{\"a\":[1,2]},\"currency\":\"BRL\",\"amount\":\"-7\"}")
                .exchange();
        assertThat(result).hasStatusOk();
        assertThat(result).bodyText().isEqualTo("{\"amount\":\"-7.00\",\"currency\":\"BRL\"}");
    }

    @Test
    void rejectsNumericAmount() {
        assertMalformed("{\"amount\":12.5,\"currency\":\"BRL\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e3", "+5", " 5", "1.234,56", "12.345", "", "abc", "1000000000000000"})
    void rejectsMalformedAmount(String amount) {
        assertMalformed("{\"amount\":\"" + amount + "\",\"currency\":\"BRL\"}");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"amount\":\"1.00\",\"currency\":\"XYZ\"}",
                "{\"amount\":\"1.00\"}",
                "{\"currency\":\"BRL\"}",
                "{\"amount\":null,\"currency\":\"BRL\"}",
                "{\"amount\":\"1.00\",\"currency\":null}",
                "{\"amount\":\"1.00\",\"currency\":55}",
                "{\"amount\":\"1.00\",\"currency\":\"XXX\"}",
                "[]",
                "\"1.00\"",
                "5"
            })
    void rejectsBadCurrencyOrShape(String body) {
        assertMalformed(body);
    }

    @Test
    void errorBodyNeverEchoesTheInput() {
        var result = post("{\"amount\":\"leaky-secret\",\"currency\":\"BRL\"}").exchange();
        assertThat(result).hasStatus(400);
        assertThat(result).bodyText().doesNotContain("leaky-secret");
    }

    @RestController
    static class MoneyEchoController {

        @PostMapping("/test/money")
        Money echo(@RequestBody Money money) {
            return money;
        }
    }
}

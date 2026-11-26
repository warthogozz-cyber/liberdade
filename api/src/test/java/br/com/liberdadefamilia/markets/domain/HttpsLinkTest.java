package br.com.liberdadefamilia.markets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpsLinkTest {

    @Test
    void onlyHttpsWithoutCredentials() {
        assertThat(HttpsLink.parse("http://mercado.example/", 500)).isEmpty();
        assertThat(HttpsLink.parse("https://u:p@mercado.example/", 500)).isEmpty();
        assertThat(HttpsLink.parse("javascript:alert(1)", 500)).isEmpty();
        assertThat(HttpsLink.parse("https://mercado.example/" + "a".repeat(500), 500))
                .isEmpty();
        assertThat(HttpsLink.parse("https://", 500)).isEmpty();
        assertThat(HttpsLink.parse("https://exa mple.com/", 500)).isEmpty();
        assertThat(HttpsLink.parse(null, 500)).isEmpty();
        assertThat(HttpsLink.parse("  ", 500)).isEmpty();
    }

    @Test
    void acceptsAPlainHttpsLink() {
        assertThat(HttpsLink.parse(" https://mercado.example/ofertas?x=1 ", 500))
                .hasValueSatisfying(uri -> assertThat(uri).hasToString("https://mercado.example/ofertas?x=1"));
    }
}

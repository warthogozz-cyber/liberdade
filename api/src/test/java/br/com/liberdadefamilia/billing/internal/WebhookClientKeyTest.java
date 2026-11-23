package br.com.liberdadefamilia.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Callers with a bad token are counted per IPv4 address and per IPv6 /64. */
class WebhookClientKeyTest {

    @Test
    void ipv4IsCountedAsIs() {
        assertThat(WebhookController.clientKey("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void addressesInOneIpv6SlashSixtyFourShareAKey() {
        String first = WebhookController.clientKey("2001:db8:1:2:aaaa::1");
        String second = WebhookController.clientKey("2001:db8:1:2:ffff:ffff:ffff:ffff");

        assertThat(first).isEqualTo(second).isEqualTo("20010db800010002/64");
    }

    @Test
    void anotherIpv6SlashSixtyFourGetsItsOwnKey() {
        assertThat(WebhookController.clientKey("2001:db8:1:3::1"))
                .isNotEqualTo(WebhookController.clientKey("2001:db8:1:2::1"));
    }
}

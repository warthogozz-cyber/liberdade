package br.com.liberdadefamilia.markets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstagramRefTest {

    @Test
    void acceptsProfileForms() {
        for (String raw :
                new String[] {"@mercado.exemplo", "mercado.exemplo", "https://www.instagram.com/mercado.exemplo/?hl=pt"
                }) {
            InstagramRef ref = InstagramRef.parse(raw).orElseThrow();
            assertThat(ref).as(raw).isInstanceOf(InstagramRef.Profile.class);
            assertThat(ref.canonicalUrl()).as(raw).isEqualTo("https://www.instagram.com/mercado.exemplo/");
        }
    }

    @Test
    void acceptsPostUrl() {
        InstagramRef ref =
                InstagramRef.parse("https://www.instagram.com/p/AbC123/?igsh=x").orElseThrow();

        assertThat(ref).isInstanceOf(InstagramRef.Post.class);
        assertThat(ref.canonicalUrl()).isEqualTo("https://www.instagram.com/p/AbC123/");
    }

    @Test
    void rejectsOthers() {
        for (String raw : new String[] {
            "http://instagram.com/x",
            "https://evil.example/p/1/",
            "https://evil.example/mercado/",
            "https://www.instagram.com.evil.example/x/",
            "a".repeat(31),
            "@" + "a".repeat(31),
            "",
            "   ",
            "com espaco",
            "https://www.instagram.com/",
            "https://u:p@www.instagram.com/x/"
        }) {
            assertThat(InstagramRef.parse(raw)).as(raw).isEmpty();
        }
        assertThat(InstagramRef.parse(null)).isEmpty();
        assertThat(InstagramRef.parse("a".repeat(30))).isPresent();
    }
}

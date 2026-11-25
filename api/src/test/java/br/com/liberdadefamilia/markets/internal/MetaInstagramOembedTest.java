package br.com.liberdadefamilia.markets.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The Instagram oEmbed client against canned answers; nothing here touches the network. */
class MetaInstagramOembedTest {

    private static final String BASE = "https://graph.test/v25.0";
    private static final String BOT = "https://app.test/robo";
    private static final String POST = "https://www.instagram.com/p/Exemplo123/";

    private MockRestServiceServer server;
    private MetaInstagramOembed oembed;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MarketsProperties properties = new MarketsProperties(
                BOT, null, null, new MarketsProperties.Instagram(true, "meta", BASE, Duration.ofSeconds(5), false));
        oembed = new MetaInstagramOembed(builder, properties);
    }

    private static String canned() throws IOException {
        try (var in = MetaInstagramOembedTest.class.getResourceAsStream("/markets/oembed-ok.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void answer(String body) {
        server.expect(requestTo(startsWith(BASE + "/instagram_oembed")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void noTokenInTheUrl() throws Exception {
        server.expect(requestTo(startsWith(BASE + "/instagram_oembed?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(requestTo(not(containsString("access_token"))))
                .andExpect(queryParam("omitscript", "true"))
                .andExpect(queryParam("maxwidth", "320"))
                .andRespond(withSuccess(canned(), MediaType.APPLICATION_JSON));

        oembed.html(POST);

        server.verify();
    }

    @Test
    void sendsTheBotUserAgent() throws Exception {
        server.expect(requestTo(startsWith(BASE + "/instagram_oembed")))
                .andExpect(header("User-Agent", "LiberdadeBot/1.0 (+" + BOT + ")"))
                .andRespond(withSuccess(canned(), MediaType.APPLICATION_JSON));

        oembed.html(POST);

        server.verify();
    }

    @Test
    void returnsTheBlockquoteHtml() throws Exception {
        answer(canned());

        assertThat(oembed.html(POST))
                .hasValueSatisfying(html -> assertThat(html)
                        .startsWith("<blockquote class=\"instagram-media\"")
                        .endsWith("</blockquote>")
                        .doesNotContain("<script"));
    }

    @Test
    void refusalIsEmpty() throws Exception {
        server.expect(requestTo(startsWith(BASE + "/instagram_oembed")))
                .andRespond(withBadRequest()
                        .body("{\"error\":{\"message\":\"nope\",\"code\":100}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThat(oembed.html("https://www.instagram.com/perfil/")).isEmpty();
    }

    @Test
    void htmlMustBeABlockquote() throws Exception {
        answer("{\"html\":\"<script async src=\\\"https://x.test/e.js\\\"></script>\"}");

        assertThat(oembed.html(POST)).isEmpty();
    }

    @Test
    void aScriptInsideTheBlockquoteIsRefused() throws Exception {
        answer("{\"html\":\"<blockquote class=\\\"instagram-media\\\"><SCRIPT>x()</SCRIPT></blockquote>\"}");

        assertThat(oembed.html(POST)).isEmpty();
    }

    @Test
    void emptyBodyIsEmpty() throws Exception {
        answer("");

        assertThat(oembed.html(POST)).isEmpty();
    }

    @Test
    void noHtmlIsEmpty() throws Exception {
        answer("{\"type\":\"rich\"}");

        assertThat(oembed.html(POST)).isEmpty();
    }

    @Test
    void upstreamErrorFails() {
        server.expect(requestTo(startsWith(BASE + "/instagram_oembed"))).andRespond(withServerError());

        assertThatThrownBy(() -> oembed.html(POST))
                .isInstanceOf(OembedException.class)
                .hasMessageNotContaining("Exemplo123");
    }

    @Test
    void notJsonFails() {
        answer("<html>oops</html>");

        assertThatThrownBy(() -> oembed.html(POST)).isInstanceOf(OembedException.class);
    }
}

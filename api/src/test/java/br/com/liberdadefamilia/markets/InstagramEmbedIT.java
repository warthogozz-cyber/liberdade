package br.com.liberdadefamilia.markets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.markets.internal.InstagramOembed;
import br.com.liberdadefamilia.markets.internal.OembedException;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * "Ver aqui": the embed route with a source that counts its calls and a clock the test moves. Nothing here reaches
 * the network. Each test uses its own post, since the cache lives as long as the context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, InstagramEmbedIT.Config.class})
class InstagramEmbedIT {

    private static final Instant BASE = Instant.parse("2026-12-01T12:00:00Z");

    /** A "privado" post is refused, a "falha" post fails, any other answers with an invented blockquote. */
    static final class CountingOembed implements InstagramOembed {

        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Optional<String> html(String canonicalUrl) throws OembedException {
            calls.incrementAndGet();
            if (canonicalUrl.contains("falha")) {
                throw new OembedException("the embed service failed");
            }
            if (canonicalUrl.contains("privado")) {
                return Optional.empty();
            }
            return Optional.of("<blockquote class=\"instagram-media\"><a href=\"" + canonicalUrl
                    + "\">Post de exemplo</a></blockquote>");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        CountingOembed countingOembed() {
            return new CountingOembed();
        }

        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    CountingOembed oembed;

    @Autowired
    TestClock clock;

    @MockitoSpyBean
    Plans plans;

    private UUID ana;
    private UUID ws;

    @BeforeEach
    void family() {
        clock.freeze(BASE);
        doReturn(false).when(plans).legalSetEnabled();
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        jdbc.sql("insert into workspace_modules (workspace_id, module_key, enabled) values (:ws, 'markets', true)")
                .param("ws", ws)
                .update();
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void returnsTheBlockquoteHtml() {
        UUID market = market("https://www.instagram.com/p/html1/");

        MvcTestResult result = embed(market);

        assertThat(result).hasStatus(200);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.html")
                .asString()
                .startsWith("<blockquote class=\"instagram-media\"")
                .doesNotContain("<script");
    }

    @Test
    void cachedForSevenDays() {
        UUID market = market("https://www.instagram.com/p/sete1/");
        int before = oembed.calls.get();

        assertThat(embed(market)).hasStatus(200);
        assertThat(embed(market)).hasStatus(200);
        assertThat(oembed.calls.get() - before).isEqualTo(1);

        clock.freeze(BASE.plus(Duration.ofDays(7)).plusSeconds(1));
        assertThat(embed(market)).hasStatus(200);
        assertThat(oembed.calls.get() - before).isEqualTo(2);
    }

    @Test
    void aRefusalIsKeptForAMinuteOnly() {
        UUID market = market("https://www.instagram.com/p/privado1/");
        int before = oembed.calls.get();

        assertThat(embed(market)).hasStatus(422);
        assertThat(embed(market)).hasStatus(422);
        assertThat(oembed.calls.get() - before).isEqualTo(1);

        clock.freeze(BASE.plus(Duration.ofMinutes(1)).plusSeconds(1));
        assertThat(embed(market)).hasStatus(422);
        assertThat(oembed.calls.get() - before).isEqualTo(2);
    }

    @Test
    void unavailableIs422() {
        UUID market = market("https://www.instagram.com/p/privado2/");

        MvcTestResult result = embed(market);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.instagram_unavailable");
    }

    @Test
    void upstreamErrorIs422() {
        UUID market = market("https://www.instagram.com/p/falha1/");
        int before = oembed.calls.get();

        MvcTestResult result = embed(market);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.instagram_unavailable");
        assertThat(embed(market)).hasStatus(422);
        assertThat(oembed.calls.get() - before).as("a failure is not kept").isEqualTo(2);
    }

    @Test
    void profileFollowsTheGate() {
        UUID market = market("@perfil_exemplo");
        int before = oembed.calls.get();

        MvcTestResult result = embed(market);

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("markets.instagram_unavailable");
        assertThat(oembed.calls.get()).isEqualTo(before);
    }

    @Test
    void noInstagramIs422WithoutCalling() {
        UUID market = MarketsTestData.family(jdbc, ws, "Feira Sem Insta", null, null);
        int before = oembed.calls.get();

        assertThat(embed(market)).hasStatus(422);
        assertThat(oembed.calls.get()).isEqualTo(before);
    }

    @Test
    void unknownMarketIs404() {
        assertThat(embed(UUID.randomUUID())).hasStatus(404);
    }

    private UUID market(String instagram) {
        UUID id = MarketsTestData.family(jdbc, ws, "Feira Exemplo", null, null);
        jdbc.sql("update markets set instagram = :instagram, instagram_set_at = now() where id = :id")
                .param("instagram", instagram)
                .param("id", id)
                .update();
        return id;
    }

    private MvcTestResult embed(UUID market) {
        var session = TestUsers.signedIn(ana, null);
        return mvc.method(HttpMethod.POST)
                .uri("/v1/workspaces/" + ws + "/markets/" + market + "/instagram-embed")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
    }
}

package br.com.liberdadefamilia.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.billing.Plans;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestClock;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The Grátis plan keeps up to three active goals; the paid plans and selling switched off have no limit. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, GoalLimitIT.Config.class})
class GoalLimitIT {

    private static final String LIMIT_DETAIL = "No plano Grátis, a família tem até 3 metas ativas. Conclua ou arquive"
            + " uma, ou veja o plano Família em Mais > Plano.";

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

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
    TestClock clock;

    @MockitoSpyBean
    Plans plans;

    private UUID ana;
    private UUID ws;

    @BeforeEach
    void family() {
        clock.freeze(Instant.parse("2026-11-20T12:00:00Z"));
        ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
    }

    @AfterEach
    void releaseClock() {
        clock.release();
    }

    @Test
    void freeStopsAtThreeActive() {
        createActive(3);

        var fourth = create("Quarta");

        assertThat(fourth).hasStatus(403);
        assertThat(fourth).bodyJson().extractingPath("$.code").isEqualTo("goal.limit-reached");
        assertThat(fourth).bodyJson().extractingPath("$.detail").isEqualTo(LIMIT_DETAIL);
        var list = send(HttpMethod.GET, "/goals", null);
        assertThat(list).bodyJson().extractingPath("$.limitReached").isEqualTo(true);
        assertThat(list).bodyJson().extractingPath("$.goals.length()").isEqualTo(3);
    }

    @Test
    void achievedAndArchivedDoNotCount() {
        List<UUID> goals = createActive(3);
        assertThat(contribute(goals.getFirst(), "100.00")).hasStatus(201);

        assertThat(send(HttpMethod.GET, "/goals", null))
                .bodyJson()
                .extractingPath("$.limitReached")
                .isEqualTo(false);
        assertThat(create("Quarta")).hasStatus(201);

        assertThat(send(HttpMethod.POST, "/goals/" + goals.get(1) + "/archive", null))
                .hasStatus(204);
        assertThat(create("Quinta")).hasStatus(201);
        assertThat(create("Sexta")).hasStatus(403);
    }

    @Test
    void familyAndFounderHaveNoLimit() {
        plan(false, Instant.parse("2027-11-20T12:00:00Z"));
        createActive(3);
        assertThat(create("Quarta")).hasStatus(201);
        assertThat(send(HttpMethod.GET, "/goals", null))
                .bodyJson()
                .extractingPath("$.limitReached")
                .isEqualTo(false);

        jdbc.sql("delete from workspace_plans where workspace_id = :ws")
                .param("ws", ws)
                .update();
        plan(true, Instant.parse("2027-11-20T12:00:00Z"));
        assertThat(create("Quinta")).hasStatus(201);
    }

    @Test
    void familyEndedKeepsGoals() {
        plan(false, Instant.parse("2026-12-20T12:00:00Z"));
        List<UUID> goals = createActive(5);

        clock.freeze(Instant.parse("2026-12-21T12:00:00Z"));

        var list = send(HttpMethod.GET, "/goals", null);
        assertThat(list).bodyJson().extractingPath("$.goals.length()").isEqualTo(5);
        assertThat(list).bodyJson().extractingPath("$.limitReached").isEqualTo(true);
        assertThat(create("Sexta")).hasStatus(403);
        assertThat(contribute(goals.getFirst(), "10.00")).hasStatus(201);
    }

    @Test
    void billingOffHasNoLimit() {
        doReturn(false).when(plans).legalSetEnabled();
        createActive(3);

        assertThat(create("Quarta")).hasStatus(201);
        assertThat(send(HttpMethod.GET, "/goals", null))
                .bodyJson()
                .extractingPath("$.limitReached")
                .isEqualTo(false);
    }

    @Test
    void concurrentCreatesStopAtThree() throws Exception {
        createActive(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> attempt = () -> {
            ready.countDown();
            go.await();
            return create("Paralela").getResponse().getStatus();
        };

        ExecutorService pool =
                Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        try {
            Future<Integer> first = pool.submit(attempt);
            Future<Integer> second = pool.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Integer> statuses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 403);
        } finally {
            pool.shutdownNow();
        }
        assertThat(activeCount()).isEqualTo(3);
    }

    private void plan(boolean founder, Instant ends) {
        jdbc.sql("""
                        insert into workspace_plans (workspace_id, plan_key, founder, starts_at, ends_at)
                        values (:ws, 'FAMILY', :founder, :starts, :ends)""")
                .param("ws", ws)
                .param("founder", founder)
                .param("starts", Timestamp.from(Instant.parse("2026-11-01T00:00:00Z")))
                .param("ends", Timestamp.from(ends))
                .update();
    }

    private int activeCount() {
        return jdbc.sql("""
                        select count(*) from goals
                        where workspace_id = :ws and archived_at is null and achieved_at is null""").param("ws", ws).query(Integer.class).single();
    }

    private List<UUID> createActive(int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(idOf(create("Meta " + (i + 1))));
        }
        return ids;
    }

    private MvcTestResult create(String name) {
        return send(HttpMethod.POST, "/goals", """
                {"name":"%s","targetAmount":{"amount":"100.00","currency":"BRL"},"icon":"savings"}""".formatted(name));
    }

    private MvcTestResult contribute(UUID goal, String amount) {
        return send(HttpMethod.POST, "/goals/" + goal + "/contributions", """
                {"amount":{"amount":"%s","currency":"BRL"},"occurredOn":"2026-11-20"}""".formatted(amount));
    }

    private static UUID idOf(MvcTestResult result) {
        assertThat(result).hasStatus(201);
        try {
            String id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
            return UUID.fromString(id);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult send(HttpMethod method, String path, @Nullable String body) {
        var request = mvc.method(method).uri("/v1/workspaces/" + ws + path).with(TestUsers.signedIn(ana, null));
        if (method != HttpMethod.GET) {
            request.with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }
}

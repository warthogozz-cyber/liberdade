package br.com.liberdadefamilia.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.live.internal.LiveStreams;
import br.com.liberdadefamilia.planning.GoalChanged;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.Eventually;
import br.com.liberdadefamilia.support.SseStreams;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.MemberProfileChanged;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

/** The project notice reaches the adults of the family, never the youth, and only while Projetos is on. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VentureLiveIT {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    LiveStreams streams;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    private UUID ana;
    private UUID bia;
    private UUID davi;
    private UUID leo;
    private UUID ws;
    private CsrfSession anaSession;
    private CsrfSession biaSession;
    private CsrfSession daviSession;
    private CsrfSession leoSession;

    @BeforeEach
    void family() {
        ana = users.create("Ana Teste", "ana" + UUID.randomUUID() + "@example.com");
        bia = users.create("Bia Teste", "bia" + UUID.randomUUID() + "@example.com");
        davi = users.create("Davi Teste", "davi" + UUID.randomUUID() + "@example.com");
        leo = users.create("Leo Teste", "leo" + UUID.randomUUID() + "@example.com");
        ws = workspaces.createFor(ana);
        workspaces.addMember(ws, bia, Role.VIEWER, "Bia");
        workspaces.addMember(ws, davi, Role.YOUTH, "Davi");
        workspaces.addMember(ws, leo, Role.ADULT, "Leo");
        anaSession = CsrfSession.start(mvc, TestUsers.signedIn(ana, null));
        biaSession = CsrfSession.start(mvc, TestUsers.signedIn(bia, null));
        daviSession = CsrfSession.start(mvc, TestUsers.signedIn(davi, null));
        leoSession = CsrfSession.start(mvc, TestUsers.signedIn(leo, null));
        module("ventures", true);
    }

    @AfterEach
    void closeEverything() {
        streams.all().forEach(streams::close);
    }

    private void module(String key, boolean enabled) {
        jdbc.sql("""
                        insert into workspace_modules (workspace_id, module_key, enabled, updated_by)
                        values (:ws, :key, :enabled, :ana)
                        on conflict (workspace_id, module_key) do update set enabled = :enabled""")
                .param("ws", ws)
                .param("key", key)
                .param("enabled", enabled)
                .param("ana", ana)
                .update();
    }

    /** A notice every stream receives, published last, so that what is not there by then was not sent. */
    private void marker(MvcResult... receivers) {
        tx.executeWithoutResult(status -> events.publishEvent(new MemberProfileChanged(ws, TestIds.v7())));
        Eventually.within(WAIT, () -> {
            for (MvcResult receiver : receivers) {
                assertThat(SseStreams.changes(receiver)).anyMatch(change -> change.contains("\"members\""));
            }
        });
    }

    private String createVenture() throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/workspaces/" + ws + "/ventures")
                        .with(TestUsers.signedIn(ana, null))
                        .with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Doces\",\"type\":\"FOOD\"}"))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private void edit(String id) throws Exception {
        MvcResult updated = mockMvc.perform(put("/v1/workspaces/" + ws + "/ventures/" + id)
                        .with(TestUsers.signedIn(ana, null))
                        .with(CsrfSession.start(mvc, TestUsers.signedIn(ana, null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":0,"name":"Doces finos","type":"FOOD","startedOn":null,
                                 "initialItems":[],"fixedItems":[],"products":[]}"""))
                .andReturn();
        assertThat(updated.getResponse().getStatus())
                .as(updated.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("aviso para o dono e o leitor")
    void adultsGetTheNotice() throws Exception {
        String id = createVenture();
        MvcResult owner = SseStreams.open(mockMvc, ws, anaSession, TestUsers.signedIn(ana, null));
        MvcResult viewer = SseStreams.open(mockMvc, ws, biaSession, TestUsers.signedIn(bia, null));
        String notice = "{\"entity\":\"venture\",\"id\":\"" + id + "\"}";

        edit(id);

        Eventually.within(WAIT, () -> {
            assertThat(SseStreams.changes(owner)).contains(notice);
            assertThat(SseStreams.changes(viewer)).contains(notice);
        });
    }

    @Test
    @DisplayName("o jovem não recebe aviso de projeto")
    void youthNeverGetsIt() throws Exception {
        String id = createVenture();
        MvcResult owner = SseStreams.open(mockMvc, ws, anaSession, TestUsers.signedIn(ana, null));
        MvcResult youth = SseStreams.open(mockMvc, ws, daviSession, TestUsers.signedIn(davi, null));

        edit(id);

        Eventually.within(
                WAIT, () -> assertThat(SseStreams.changes(owner)).anyMatch(change -> change.contains("\"venture\"")));
        marker(owner, youth);
        assertThat(SseStreams.changes(youth)).noneMatch(change -> change.contains("\"venture\""));
    }

    @Test
    @DisplayName("módulo desligado, ninguém recebe")
    void moduleOffSendsNothing() throws Exception {
        String id = createVenture();
        MvcResult owner = SseStreams.open(mockMvc, ws, anaSession, TestUsers.signedIn(ana, null));
        MvcResult adult = SseStreams.open(mockMvc, ws, leoSession, TestUsers.signedIn(leo, null));

        module("ventures", false);
        tx.executeWithoutResult(status ->
                events.publishEvent(new br.com.liberdadefamilia.ventures.VentureChanged(ws, UUID.fromString(id))));

        marker(owner, adult);
        assertThat(SseStreams.changes(owner)).noneMatch(change -> change.contains("\"venture\""));
        assertThat(SseStreams.changes(adult)).noneMatch(change -> change.contains("\"venture\""));
    }

    @Test
    @DisplayName("os avisos de metas seguem iguais")
    void planningNoticesUnchanged() throws Exception {
        MvcResult owner = SseStreams.open(mockMvc, ws, anaSession, TestUsers.signedIn(ana, null));
        UUID goal = TestIds.v7();
        String notice = "{\"entity\":\"goal\",\"id\":\"" + goal + "\"}";

        // Projetos on does not stand in for Planejamento.
        module("planning", false);
        tx.executeWithoutResult(status -> events.publishEvent(new GoalChanged(ws, goal)));
        marker(owner);
        assertThat(SseStreams.changes(owner)).doesNotContain(notice);

        module("planning", true);
        tx.executeWithoutResult(status -> events.publishEvent(new GoalChanged(ws, goal)));
        Eventually.within(WAIT, () -> assertThat(SseStreams.changes(owner)).contains(notice));
    }
}

package br.com.liberdadefamilia.ledger;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** The family's tags: made by typing a name on a transaction, listed, renamed, archived and used as a filter. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TagsIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    private UUID ana;
    private UUID workspaceId;
    private UUID walletId;
    private UUID mercadoId;

    @BeforeEach
    void family() {
        ana = users.create("Ana Teste", "ana@example.com");
        workspaceId = workspaces.createFor(ana);
        walletId = ledger.walletOf(workspaceId);
        mercadoId = ledger.categoryOf(workspaceId, "EXPENSE", "Mercado");
    }

    @Test
    void createTransactionCreatesTags() {
        UUID id = TestIds.v7();

        var created = post(ana, id, expense(id, walletId, "Escola", "viagem"));

        assertThat(created).hasStatus(201);
        assertThat(tagsOf(created)).containsExactly("Escola", "viagem");
        assertThat(names(get(ana, "/tags"))).containsExactly("Escola", "viagem");
        assertThat(tagsOf(get(ana, "/transactions/" + id))).containsExactly("Escola", "viagem");
    }

    @Test
    void reuseIgnoresCase() {
        UUID first = TestIds.v7();
        UUID second = TestIds.v7();
        assertThat(post(ana, first, expense(first, walletId, "Escola"))).hasStatus(201);

        var reused = post(ana, second, expense(second, walletId, "ESCOLA"));

        assertThat(reused).hasStatus(201);
        assertThat(tagsOf(reused)).containsExactly("Escola");
        assertThat(names(get(ana, "/tags"))).containsExactly("Escola");
        assertThat(count("select count(*) from tags where workspace_id = :ws")).isEqualTo(1);
    }

    @Test
    void patchNullKeepsListReplaces() {
        UUID id = TestIds.v7();
        assertThat(post(ana, id, expense(id, walletId, "Escola", "viagem"))).hasStatus(201);

        var kept = patch(ana, id, edit(id, 0));
        assertThat(kept).hasStatusOk();
        assertThat(tagsOf(kept)).containsExactly("Escola", "viagem");

        Map<String, Object> replace = edit(id, 1);
        replace.put("tags", List.of("Lazer"));
        var replaced = patch(ana, id, replace);
        assertThat(replaced).hasStatusOk();
        assertThat(tagsOf(replaced)).containsExactly("Lazer");

        Map<String, Object> clear = edit(id, 2);
        clear.put("tags", List.of());
        var cleared = patch(ana, id, clear);
        assertThat(cleared).hasStatusOk();
        assertThat(tagsOf(cleared)).isEmpty();
    }

    @Test
    void limit200() {
        jdbc.sql("""
                        insert into tags (workspace_id, name)
                        select :ws, 'tag' || n from generate_series(1, 200) n""").param("ws", workspaceId).update();
        UUID rejected = TestIds.v7();

        var over = post(ana, rejected, expense(rejected, walletId, "Nova"));

        assertThat(over).hasStatus(422);
        assertThat(over).bodyJson().isLenientlyEqualTo("""
                {"code":"tag.limit","detail":"A família já tem 200 tags. Arquive alguma para criar outra."}""");
        assertThat(count("select count(*) from transactions where workspace_id = :ws"))
                .isZero();
        UUID existing = TestIds.v7();
        assertThat(post(ana, existing, expense(existing, walletId, "TAG7"))).hasStatus(201);
    }

    @Test
    void renameTakenConflicts() {
        UUID id = TestIds.v7();
        assertThat(post(ana, id, expense(id, walletId, "Escola", "viagem"))).hasStatus(201);

        var result = patchTag(ana, tagId("viagem"), "escola");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("tag.name-taken");
        var renamed = patchTag(ana, tagId("viagem"), "  Passeio ");
        assertThat(renamed).hasStatusOk();
        assertThat(renamed).bodyJson().extractingPath("$.name").isEqualTo("Passeio");
        assertThat(patchTag(ana, tagId("Passeio"), "PASSEIO")).hasStatusOk();
    }

    @Test
    void archiveHidesFromListKeepsOnTransaction() {
        UUID id = TestIds.v7();
        assertThat(post(ana, id, expense(id, walletId, "Escola", "viagem"))).hasStatus(201);
        UUID escola = tagId("Escola");

        assertThat(archive(ana, escola)).hasStatus(204);
        assertThat(archive(ana, escola)).hasStatus(204);

        assertThat(names(get(ana, "/tags"))).containsExactly("viagem");
        assertThat(tagsOf(get(ana, "/transactions/" + id))).containsExactly("Escola", "viagem");
        UUID again = TestIds.v7();
        var recreated = post(ana, again, expense(again, walletId, "Escola"));
        assertThat(recreated).hasStatus(201);
        assertThat(names(get(ana, "/tags"))).containsExactly("Escola", "viagem");
        assertThat(count("select count(*) from tags where workspace_id = :ws and lower(name) = 'escola'"))
                .isEqualTo(2);
    }

    @Test
    void listFiltersByTag() {
        UUID trip = TestIds.v7();
        UUID school = TestIds.v7();
        UUID plain = TestIds.v7();
        assertThat(post(ana, trip, expense(trip, walletId, "viagem"))).hasStatus(201);
        assertThat(post(ana, school, expense(school, walletId, "Escola"))).hasStatus(201);
        assertThat(post(ana, plain, expense(plain, walletId))).hasStatus(201);

        var result = get(ana, "/transactions?month=2026-11&tagId=" + tagId("viagem"));

        assertThat(result).hasStatusOk();
        List<String> ids = JsonPath.read(text(result), "$[*].id");
        assertThat(ids).containsExactly(trip.toString());
    }

    @Test
    void otherFamilyTagIsNotFound() {
        UUID other = users.create("Outra Teste", "outra@example.com");
        UUID otherWorkspace = workspaces.createFor(other);
        UUID foreignTag = jdbc.sql("insert into tags (workspace_id, name) values (:ws, 'Alheia') returning id")
                .param("ws", otherWorkspace)
                .query(UUID.class)
                .single();

        var renamed = patchTag(ana, foreignTag, "Minha");
        var archived = archive(ana, foreignTag);

        assertThat(renamed).hasStatus(404);
        assertThat(renamed).bodyJson().extractingPath("$.code").isEqualTo("tag.not-found");
        assertThat(archived).hasStatus(404);
        assertThat(archived).bodyJson().extractingPath("$.code").isEqualTo("tag.not-found");
        assertThat(count("select count(*) from tags where id = :tag and archived_at is null", foreignTag))
                .isEqualTo(1);
    }

    @Test
    void youthSeesOnlyTagsOfVisibleTransactions() {
        UUID leo = users.create("Leo Teste", "leo@example.com");
        UUID leoMembership = workspaces.addMember(workspaceId, leo, YOUTH, "Leo");
        UUID leoWallet = account("Carteira de Leo", leoMembership);
        UUID corrente = account("Corrente", null);
        UUID family = TestIds.v7();
        assertThat(post(ana, family, expense(family, corrente, "Presente"))).hasStatus(201);
        assertThat(names(get(leo, "/tags"))).doesNotContain("Presente");

        UUID own = TestIds.v7();
        var reused = post(leo, own, expense(own, leoWallet, "Presente"));

        assertThat(reused).hasStatus(201);
        assertThat(tagsOf(reused)).containsExactly("Presente");
        assertThat(count("select count(*) from tags where workspace_id = :ws")).isEqualTo(1);
        assertThat(names(get(leo, "/tags"))).containsExactly("Presente");
    }

    @Test
    void youthAndViewerCannotRename() {
        UUID id = TestIds.v7();
        assertThat(post(ana, id, expense(id, walletId, "Escola"))).hasStatus(201);
        UUID escola = tagId("Escola");
        UUID leo = users.create("Leo Teste", "leo@example.com");
        workspaces.addMember(workspaceId, leo, YOUTH, "Leo");
        UUID vera = users.create("Vera Teste", "vera@example.com");
        workspaces.addMember(workspaceId, vera, VIEWER, "Vera");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        workspaces.addMember(workspaceId, bia, ADULT, "Bia");

        for (UUID caller : List.of(leo, vera)) {
            assertThat(patchTag(caller, escola, "Outra")).hasStatus(403);
            assertThat(archive(caller, escola)).hasStatus(403);
        }

        assertThat(names(get(ana, "/tags"))).containsExactly("Escola");
        assertThat(patchTag(bia, escola, "Colégio")).hasStatusOk();
    }

    @Test
    void retryWithOtherTagsConflicts() {
        UUID id = TestIds.v7();
        assertThat(post(ana, id, expense(id, walletId, "Escola"))).hasStatus(201);

        var retry = post(ana, id, expense(id, walletId, "Viagem"));

        assertThat(retry).hasStatus(409);
        assertThat(retry).bodyJson().extractingPath("$.code").isEqualTo("transaction.id-conflict");
        assertThat(post(ana, id, expense(id, walletId, "ESCOLA"))).hasStatus(200);
        assertThat(names(get(ana, "/tags"))).containsExactly("Escola");
    }

    @Test
    void invalidNamesAreRejected() {
        UUID id = TestIds.v7();

        var result = post(ana, id, expense(id, walletId, "a", "b", "c", "d", "e", "f"));

        assertThat(result).hasStatus(422);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
    }

    private UUID account(String name, @Nullable UUID owner) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, owner_membership_id)
                        values (:ws, :name, 'CHECKING', 'BRL', 1000, :owner)
                        returning id""")
                .param("ws", workspaceId)
                .param("name", name)
                .param("owner", owner)
                .query(UUID.class)
                .single();
    }

    private UUID tagId(String name) {
        return jdbc.sql("""
                        select id from tags
                        where workspace_id = :ws and name = :name and archived_at is null""")
                .param("ws", workspaceId)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private long count(String sql) {
        return jdbc.sql(sql).param("ws", workspaceId).query(Long.class).single();
    }

    private long count(String sql, UUID tag) {
        return jdbc.sql(sql).param("tag", tag).query(Long.class).single();
    }

    private Map<String, Object> expense(UUID id, UUID account, String... tags) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id.toString());
        body.put("kind", "EXPENSE");
        body.put("amount", Map.of("amount", "12.34", "currency", "BRL"));
        body.put("occurredOn", "2026-11-02");
        body.put("accountId", account.toString());
        body.put("categoryId", mercadoId.toString());
        body.put("tags", List.of(tags));
        return body;
    }

    /** An edit of the wallet expense that leaves {@code tags} out. */
    private Map<String, Object> edit(UUID id, long version) {
        Map<String, Object> body = expense(id, walletId);
        body.remove("id");
        body.remove("tags");
        body.put("version", version);
        return body;
    }

    private MvcTestResult post(UUID caller, UUID id, Map<String, Object> body) {
        return mvc.post()
                .uri("/v1/workspaces/" + workspaceId + "/transactions")
                .with(TestUsers.signedIn(caller, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(caller, null)))
                .header("Idempotency-Key", id.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))
                .exchange();
    }

    private MvcTestResult patch(UUID caller, UUID id, Map<String, Object> body) {
        return send(caller, "PATCH", "/transactions/" + id, JSON.writeValueAsString(body));
    }

    private MvcTestResult patchTag(UUID caller, UUID tag, String name) {
        return send(caller, "PATCH", "/tags/" + tag, JSON.writeValueAsString(Map.of("name", name)));
    }

    private MvcTestResult archive(UUID caller, UUID tag) {
        return send(caller, "POST", "/tags/" + tag + "/archive", null);
    }

    private MvcTestResult send(UUID caller, String method, String path, @Nullable String json) {
        var request = "PATCH".equals(method) ? mvc.patch() : mvc.post();
        request.uri("/v1/workspaces/" + workspaceId + path)
                .with(TestUsers.signedIn(caller, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(caller, null)));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return request.exchange();
    }

    private MvcTestResult get(UUID caller, String path) {
        return mvc.get()
                .uri("/v1/workspaces/" + workspaceId + path)
                .with(TestUsers.signedIn(caller, null))
                .exchange();
    }

    private static List<String> tagsOf(MvcTestResult result) {
        return JsonPath.read(text(result), "$.tags");
    }

    private static List<String> names(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        return JsonPath.read(text(result), "$[*].name");
    }

    private static String text(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}

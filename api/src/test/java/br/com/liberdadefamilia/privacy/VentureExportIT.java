package br.com.liberdadefamilia.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The ventures in the data export: the family's zip has all of them, a person's zip only the ones they created. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VentureExportIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    private UUID ana;
    private UUID bia;
    private UUID ws;
    private UUID anaMember;
    private UUID biaMember;

    @BeforeEach
    void family() {
        ana = users.create("Ana Teste", "ana@example.com");
        bia = users.create("Bia Teste", "bia@example.com");
        ws = workspaces.createFor(ana);
        anaMember = workspaces.membershipOf(ws, ana);
        biaMember = workspaces.addMember(ws, bia, Role.ADULT, "Bibi");
    }

    @Test
    void familyExportHasVentures() {
        UUID doces = venture("Doces", "FOOD", "ACTIVE", "2026-10-01", null, "food-sweets", anaMember);
        item(doces, "FIXED_MONTHLY", "Gas", "270.00", 1);
        item(doces, "INITIAL", "Batedeira", "300.00", 0);
        item(doces, "INITIAL", "Panelas", "350.50", 1);
        product(doces, "Brigadeiro", "1.80", "5.00", 300, 0);
        step(doces, "Testar a receita", 0, true);
        step(doces, "Abrir o perfil", 1, false);
        UUID loja = venture("Loja", "RESALE", "CLOSED", "2026-01-10", "2026-06-30", null, biaMember);

        Map<String, byte[]> files = unzip(exportFamily(ana, ws));
        String json = text(files.get("dados.json"));

        assertThat(JsonPath.<Integer>read(json, "$.formatVersion")).isEqualTo(8);
        assertThat(JsonPath.<List<String>>read(json, "$.ventures[*].id"))
                .containsExactly(doces.toString(), loja.toString());
        assertThat(JsonPath.<String>read(json, "$.ventures[0].name")).isEqualTo("Doces");
        assertThat(JsonPath.<String>read(json, "$.ventures[0].status")).isEqualTo("ACTIVE");
        assertThat(JsonPath.<String>read(json, "$.ventures[0].templateKey")).isEqualTo("food-sweets");
        assertThat(JsonPath.<String>read(json, "$.ventures[1].closedOn")).isEqualTo("2026-06-30");
        assertThat(JsonPath.<List<String>>read(json, "$.ventures[0].items[*].name"))
                .containsExactly("Batedeira", "Gas", "Panelas");
        assertThat(JsonPath.<String>read(json, "$.ventures[0].products[0].unitPrice.amount"))
                .isEqualTo("5.00");
        assertThat(JsonPath.<List<String>>read(json, "$.ventures[0].steps[*].title"))
                .containsExactly("Testar a receita", "Abrir o perfil");
        assertThat(JsonPath.<Object>read(json, "$.ventures[0].steps[1].doneAt")).isNull();

        assertThat(firstLine(files.get("projetos.csv"))).endsWith("nome;tipo;situacao;inicio;encerramento;moeda");
        assertThat(text(files.get("projetos.csv")))
                .contains("Doces;Culinária;Ativo;2026-10-01;;BRL")
                .contains("Loja;Revenda;Encerrado;2026-01-10;2026-06-30;BRL");
        assertThat(firstLine(files.get("projetos_itens.csv"))).endsWith("projeto;tipo;nome;valor;moeda");
        assertThat(text(files.get("projetos_itens.csv"))).contains("Doces;").contains("350,50;BRL");
        assertThat(firstLine(files.get("projetos_produtos.csv")))
                .endsWith("projeto;nome;custo_unitario;preco;venda_prevista_mes;moeda");
        assertThat(text(files.get("projetos_produtos.csv"))).contains("Brigadeiro;1,80;5,00;300;BRL");
        assertThat(firstLine(files.get("projetos_passos.csv"))).endsWith("projeto;posicao;passo;feito_em");
        assertThat(text(files.get("projetos_passos.csv")))
                .contains("Doces;1;Testar a receita;")
                .contains("Doces;2;Abrir o perfil;");
    }

    @Test
    void personExportHasOwnVentures() {
        venture("Doces", "FOOD", "PLANNING", null, null, null, anaMember);
        UUID biasVenture = venture("Bijuterias", "RESALE", "PLANNING", null, null, null, biaMember);
        item(biasVenture, "INITIAL", "Contas", "40.00", 0);

        String json = text(unzip(exportMine(bia)).get("dados.json"));

        assertThat(JsonPath.<Integer>read(json, "$.formatVersion")).isEqualTo(8);
        assertThat(JsonPath.<List<String>>read(json, "$.ventures[*].id")).containsExactly(biasVenture.toString());
        assertThat(JsonPath.<String>read(json, "$.ventures[0].familyName")).isNotEmpty();
        assertThat(JsonPath.<String>read(json, "$.ventures[0].items[0].name")).isEqualTo("Contas");
    }

    @Test
    void leaverKeepsVenturesWithNullCreator() {
        UUID biasVenture = venture("Bijuterias", "RESALE", "PLANNING", null, null, null, biaMember);

        jdbc.sql("delete from memberships where id = :id")
                .param("id", biaMember)
                .update();

        assertThat(jdbc.sql("select created_by is null from ventures where id = :id")
                        .param("id", biasVenture)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        String json = text(unzip(exportFamily(ana, ws)).get("dados.json"));
        assertThat(JsonPath.<List<String>>read(json, "$.ventures[*].id")).containsExactly(biasVenture.toString());
    }

    @Test
    void transactionsCarryVenture() {
        UUID doces = venture("Doces", "FOOD", "ACTIVE", "2026-10-01", null, "food-sweets", anaMember);
        UUID account = account("Conta da casa", null);
        UUID marked = transaction(account, "INCOME", "Venda de bolo", anaMember, doces);
        UUID plain = transaction(account, "EXPENSE", "Mercado", anaMember, null);

        Map<String, byte[]> familyFiles = unzip(exportFamily(ana, ws));
        String family = text(familyFiles.get("dados.json"));
        assertThat(JsonPath.<Integer>read(family, "$.formatVersion")).isEqualTo(8);
        assertThat(JsonPath.<List<String>>read(family, "$.transactions[?(@.id=='" + marked + "')].ventureId"))
                .containsExactly(doces.toString());
        assertThat(JsonPath.<List<Object>>read(family, "$.transactions[?(@.id=='" + plain + "')].ventureId"))
                .containsExactly((Object) null);

        assertThat(firstLine(familyFiles.get("lancamentos.csv"))).endsWith(";tags;projeto");
        assertThat(text(familyFiles.get("lancamentos.csv")))
                .containsPattern("Venda de bolo[^\n]*;Doces\r?\n")
                .containsPattern("Mercado[^\n]*;\r?\n");

        Map<String, byte[]> mine = unzip(exportMine(ana));
        assertThat(JsonPath.<List<String>>read(
                        text(mine.get("dados.json")), "$.transactions[?(@.id=='" + marked + "')].ventureId"))
                .containsExactly(doces.toString());
        assertThat(firstLine(mine.get("lancamentos.csv"))).endsWith(";tags;projeto");
        assertThat(text(mine.get("lancamentos.csv"))).containsPattern("Venda de bolo[^\n]*;Doces\r?\n");
    }

    @Test
    void youthPersonExportHasNoVenture() {
        UUID davi = users.create("Davi Teste", "davi@example.com");
        UUID daviMember = workspaces.addMember(ws, davi, Role.YOUTH, "Davi");
        UUID doces = venture("Doces", "FOOD", "ACTIVE", "2026-10-01", null, "food-sweets", anaMember);
        UUID account = account("Mesada", daviMember);
        transaction(account, "INCOME", "Venda da mesada", daviMember, doces);

        Map<String, byte[]> files = unzip(exportMine(davi));

        String json = text(files.get("dados.json"));
        assertThat(JsonPath.<List<String>>read(json, "$.transactions[*].description"))
                .containsExactly("Venda da mesada");
        assertThat(json).doesNotContain(doces.toString());
        assertThat(JsonPath.<List<Object>>read(json, "$.ventures")).isEmpty();
        assertThat(text(files.get("lancamentos.csv"))).doesNotContain("Doces");
    }

    // --- seed ---

    private UUID account(String name, UUID owner) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, owner_membership_id)
                        values (:ws, :name, 'CHECKING', 'BRL', 0, :owner)
                        returning id""")
                .param("ws", ws)
                .param("name", name)
                .param("owner", owner)
                .query(UUID.class)
                .single();
    }

    private UUID transaction(UUID account, String kind, String description, UUID member, UUID venture) {
        UUID id = TestIds.v7();
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, category_id, description,
                             member_id, created_by, venture_id)
                        values (:id, :ws, :kind, 10, 'BRL', date '2026-11-10', :account,
                                (select id from categories where workspace_id = :ws and kind = :kind
                                 and parent_id is null order by name limit 1), :description, :member,
                                :member, :venture)""")
                .param("id", id)
                .param("ws", ws)
                .param("kind", kind)
                .param("account", account)
                .param("description", description)
                .param("member", member)
                .param("venture", venture)
                .update();
        return id;
    }

    private UUID venture(
            String name,
            String type,
            String status,
            String startedOn,
            String closedOn,
            String templateKey,
            UUID createdBy) {
        return jdbc.sql("""
                        insert into ventures (workspace_id, name, type, status, started_on, closed_on, currency,
                                              template_key, created_by)
                        values (:ws, :name, :type, :status, cast(:startedOn as date), cast(:closedOn as date), 'BRL',
                                :templateKey, :createdBy)
                        returning id""")
                .param("ws", ws)
                .param("name", name)
                .param("type", type)
                .param("status", status)
                .param("startedOn", startedOn)
                .param("closedOn", closedOn)
                .param("templateKey", templateKey)
                .param("createdBy", createdBy)
                .query(UUID.class)
                .single();
    }

    private void item(UUID venture, String kind, String name, String amount, int position) {
        jdbc.sql("""
                        insert into venture_cost_items (workspace_id, venture_id, kind, name, amount, currency, position)
                        values (:ws, :venture, :kind, :name, cast(:amount as numeric), 'BRL', :position)""")
                .param("ws", ws)
                .param("venture", venture)
                .param("kind", kind)
                .param("name", name)
                .param("amount", amount)
                .param("position", position)
                .update();
    }

    private void product(UUID venture, String name, String cost, String price, int units, int position) {
        jdbc.sql("""
                        insert into venture_products (workspace_id, venture_id, name, unit_variable_cost, unit_price,
                                                      planned_units_month, currency, position)
                        values (:ws, :venture, :name, cast(:cost as numeric), cast(:price as numeric), :units, 'BRL',
                                :position)""")
                .param("ws", ws)
                .param("venture", venture)
                .param("name", name)
                .param("cost", cost)
                .param("price", price)
                .param("units", units)
                .param("position", position)
                .update();
    }

    private void step(UUID venture, String title, int position, boolean done) {
        jdbc.sql("""
                        insert into venture_steps (workspace_id, venture_id, title, position, done_at)
                        values (:ws, :venture, :title, :position, case when :done then now() end)""")
                .param("ws", ws)
                .param("venture", venture)
                .param("title", title)
                .param("position", position)
                .param("done", done)
                .update();
    }

    // --- requests ---

    private MvcTestResult exportMine(UUID user) {
        return export("/v1/me/export", user);
    }

    private MvcTestResult exportFamily(UUID user, UUID workspace) {
        return export("/v1/workspaces/" + workspace + "/export", user);
    }

    private MvcTestResult export(String uri, UUID user) {
        var session = TestUsers.signedIn(user, null);
        return mvc.post()
                .uri(uri)
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
    }

    private static Map<String, byte[]> unzip(MvcTestResult result) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip =
                new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                files.put(entry.getName(), zip.readAllBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String firstLine(byte[] bytes) {
        return text(bytes).lines().findFirst().orElse("");
    }
}

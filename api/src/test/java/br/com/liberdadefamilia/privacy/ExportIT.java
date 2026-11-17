package br.com.liberdadefamilia.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The data export: a zip of JSON and spreadsheet-safe CSV, for the person and for the family's owner. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExportIT {

    private static final String FILE_NAME = "liberdade-(meus-dados|familia)-\\d{4}-\\d{2}-\\d{2}\\.zip";

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

    @Test
    void theWebAppCanReadTheFileNameAcrossOrigins() {
        Family f = family();
        var session = TestUsers.signedIn(f.bia, null);

        MvcTestResult result = mvc.post()
                .uri("/v1/me/export")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .contains("Content-Disposition");
    }

    @Test
    void myExportHasMyData() {
        Family f = family();

        MvcTestResult result = exportMine(f.bia);

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentType()).isEqualTo("application/zip");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .matches("attachment; filename=\"" + FILE_NAME + "\"");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        Map<String, byte[]> files = unzip(result);
        assertThat(files).containsOnlyKeys("dados.json", "lancamentos.csv", "atividade.csv");
        String json = text(files.get("dados.json"));
        assertThat(JsonPath.<Integer>read(json, "$.formatVersion")).isEqualTo(1);
        assertThat(JsonPath.<String>read(json, "$.profile.email")).isEqualTo("bia@example.com");
        assertThat(JsonPath.<String>read(json, "$.profile.displayName")).isEqualTo("Bia Teste");
        assertThat(JsonPath.<List<?>>read(json, "$.memberships")).hasSize(2);
        assertThat(JsonPath.<List<String>>read(json, "$.memberships[*].status"))
                .containsExactlyInAnyOrder("ACTIVE", "REMOVED");
        assertThat(JsonPath.<List<String>>read(json, "$.memberships[*].membershipId"))
                .doesNotContainNull()
                .hasSize(2);
        assertThat(JsonPath.<List<String>>read(json, "$.transactions[*].who")).contains("Bibi");
        assertThat(JsonPath.<List<?>>read(json, "$.consents")).isEmpty();
        assertThat(files.get("lancamentos.csv")).isNotEmpty();
        assertThat(text(files.get("atividade.csv"))).contains("quando;acao;quem;navegador;sistema");
        assertThat(json).doesNotContain("ana@example.com").doesNotContain("cris@example.com");
        assertThat(text(files.get("lancamentos.csv"))).doesNotContain("@example.com");
    }

    @Test
    void exportIncludesAcceptedDocuments() {
        Family f = family();
        jdbc.sql("""
                        insert into consents (user_id, workspace_id, purpose, document_version, granted_by)
                        values (:user, null, 'TERMS', 'terms-2026-11', :user),
                               (:user, null, 'PRIVACY', 'privacy-2026-11', :user)""").param("user", f.bia).update();

        String json = text(unzip(exportMine(f.bia)).get("dados.json"));

        assertThat(JsonPath.<List<?>>read(json, "$.consents")).hasSize(2);
        assertThat(JsonPath.<List<String>>read(json, "$.consents[*].purpose"))
                .containsExactlyInAnyOrder("TERMS", "PRIVACY");
        assertThat(JsonPath.<List<String>>read(json, "$.consents[*].documentVersion"))
                .containsExactlyInAnyOrder("terms-2026-11", "privacy-2026-11");
        assertThat(JsonPath.<List<Object>>read(json, "$.consents[*].workspaceId"))
                .containsOnlyNulls();
        assertThat(JsonPath.<List<Object>>read(json, "$.consents[*].familyName"))
                .containsOnlyNulls();
    }

    @Test
    void myExportHasOnlyMyTransactions() {
        Family f = family();

        Map<String, byte[]> files = unzip(exportMine(f.bia));

        String csv = text(files.get("lancamentos.csv"));
        assertThat(csv)
                .contains("Para a Bia")
                .contains("Feita pela Dona para a Bia")
                .doesNotContain("So da Dona");
        assertThat(csv)
                .startsWith(
                        "﻿familia;data;tipo;valor;moeda;conta;conta_destino;categoria;descricao;quem;criado_em\r\n");
        assertThat(csv).contains("Família Teste;");
        assertThat(JsonPath.<List<String>>read(text(files.get("dados.json")), "$.transactions[*].description"))
                .doesNotContain("So da Dona");
    }

    @Test
    void aYouthExportFollowsTheAccountsTheyOwn() {
        Family f = family();
        UUID teen = users.create("Davi Teste", "davi@example.com");
        UUID teenMember = workspaces.addMember(f.ws, teen, Role.YOUTH, "Davi");
        UUID anaMember = workspaces.membershipOf(f.ws, f.ana);
        UUID market = ledger.categoryOf(f.ws, "EXPENSE", "Mercado");
        UUID familyAccount = account(f.ws, "Conta da casa", "CHECKING", "0.00");
        UUID teenAccount = account(f.ws, "Mesada", "CHECKING", "0.00");
        jdbc.sql("update accounts set owner_membership_id = :m where id = :id")
                .param("m", teenMember)
                .param("id", teenAccount)
                .update();
        transaction(
                f.ws,
                familyAccount,
                null,
                market,
                "EXPENSE",
                "3.00",
                "Casa em nome do Davi",
                teenMember,
                anaMember,
                false);
        transaction(f.ws, teenAccount, null, market, "EXPENSE", "4.00", "Da mesada", teenMember, anaMember, false);

        Map<String, byte[]> files = unzip(exportMine(teen));

        String csv = text(files.get("lancamentos.csv"));
        assertThat(csv).contains("Da mesada").doesNotContain("Casa em nome do Davi");
        assertThat(JsonPath.<List<String>>read(text(files.get("dados.json")), "$.transactions[*].description"))
                .containsExactly("Da mesada");
    }

    @Test
    void myExportCoversRemovedMembershipsToo() {
        Family f = family();

        String csv = text(unzip(exportMine(f.bia)).get("lancamentos.csv"));

        assertThat(csv).contains("Familia Antiga;").contains("Mercado antigo");
    }

    @Test
    void familyExportHasEverything() {
        Family f = family();

        MvcTestResult result = exportFamily(f.ana, f.ws);

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .matches("attachment; filename=\"liberdade-familia-\\d{4}-\\d{2}-\\d{2}\\.zip\"");
        Map<String, byte[]> files = unzip(result);
        assertThat(files)
                .containsOnlyKeys("dados.json", "lancamentos.csv", "contas.csv", "categorias.csv", "membros.csv");
        String csv = text(files.get("lancamentos.csv"));
        assertThat(csv).startsWith("﻿data;tipo;valor;moeda;conta;conta_destino;categoria;descricao;quem;criado_em\r\n");
        assertThat(csv).containsPattern(";Gasto;1234,56;BRL;Corrente;;Mercado;Compra grande;Aninha;");
        assertThat(csv).contains(";Transferência;100,00;BRL;Corrente;Carteira;;;Aninha;");
        assertThat(csv).contains("So da Dona").contains("Para a Bia");
        String accounts = text(files.get("contas.csv"));
        assertThat(accounts).contains("nome;tipo;saldo_inicial;saldo;moeda;titular;arquivada_em");
        assertThat(accounts)
                .contains("Corrente;Conta corrente;10,00;-1352,56;BRL;;")
                .contains("Carteira;Dinheiro;");
        assertThat(text(files.get("categorias.csv")))
                .contains("nome;tipo;categoria_pai;arquivada_em")
                .contains("Mercado;Gasto");
        assertThat(text(files.get("membros.csv")))
                .contains("apelido;papel;situacao")
                .contains("Aninha;Dono;Ativo")
                .contains("Bibi;Adulto;Ativo");
        String json = text(files.get("dados.json"));
        assertThat(JsonPath.<String>read(json, "$.workspace.name")).isEqualTo("Família Teste");
        assertThat(JsonPath.<List<?>>read(json, "$.members")).hasSize(2);
        assertThat(JsonPath.<Boolean>read(json, "$.modules.planning")).isFalse();
        assertThat(JsonPath.<String>read(json, "$.transactions[0].amount.currency"))
                .isEqualTo("BRL");
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            assertThat(text(file.getValue())).as(file.getKey()).doesNotContain("@example.com");
        }
    }

    @Test
    void csvInjectionNeutralized() {
        Family f = family();

        String csv = text(unzip(exportFamily(f.ana, f.ws)).get("lancamentos.csv"));

        assertThat(csv).contains(";'=1+1;").doesNotContain(";=1+1;");
    }

    @Test
    void deletedTransactionsExcluded() {
        Family f = family();

        Map<String, byte[]> files = unzip(exportFamily(f.ana, f.ws));

        assertThat(text(files.get("lancamentos.csv"))).doesNotContain("Apagada");
        assertThat(text(files.get("dados.json"))).doesNotContain("Apagada");
        assertThat(text(unzip(exportMine(f.bia)).get("lancamentos.csv"))).doesNotContain("Apagada");
    }

    @Test
    void exportNeedsRecentSignIn() {
        Family f = family();

        MvcTestResult mine = export("/v1/me/export", TestUsers.signedInMinutesAgo(f.bia, 11), f.bia);
        MvcTestResult family =
                export("/v1/workspaces/" + f.ws + "/export", TestUsers.signedInMinutesAgo(f.ana, 11), f.ana);

        assertThat(mine).hasStatus(403);
        assertThat(mine).bodyJson().extractingPath("$.code").isEqualTo("auth.reauthentication-required");
        assertThat(family).hasStatus(403);
        assertThat(family).bodyJson().extractingPath("$.code").isEqualTo("auth.reauthentication-required");
    }

    @Test
    void exportNeedsSignIn() {
        assertThat(mvc.post().uri("/v1/me/export").with(CsrfSession.start(mvc)).exchange())
                .hasStatus(401);
    }

    @Test
    void adultCannotExportFamily() {
        Family f = family();

        MvcTestResult result = exportFamily(f.bia, f.ws);

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("auth.forbidden");
    }

    @Test
    void exportIsAudited() {
        Family f = family();

        assertThat(exportMine(f.bia)).hasStatusOk();
        assertThat(exportFamily(f.ana, f.ws)).hasStatusOk();

        MvcTestResult biaActivity = mvc.get()
                .uri("/v1/me/activity")
                .with(TestUsers.signedIn(f.bia, null))
                .exchange();
        assertThat(biaActivity).hasStatusOk();
        assertThat(biaActivity).bodyJson().extractingPath("$[0].action").isEqualTo("data.exported");
        assertThat(biaActivity).bodyJson().extractingPath("$[0].metadata.scope").isEqualTo("ME");
        assertThat(biaActivity).bodyJson().extractingPath("$[0].byMe").isEqualTo(true);

        MvcTestResult anaActivity = mvc.get()
                .uri("/v1/me/activity")
                .with(TestUsers.signedIn(f.ana, null))
                .exchange();
        assertThat(anaActivity).bodyJson().extractingPath("$[0].action").isEqualTo("data.exported");
        assertThat(anaActivity).bodyJson().extractingPath("$[0].metadata.scope").isEqualTo("FAMILY");
        Map<String, Object> row = jdbc.sql("""
                        select workspace_id, target_type, target_id from audit_events
                        where action = 'data.exported' and actor_user_id = :ana""").param("ana", f.ana).query().singleRow();
        assertThat(row)
                .containsEntry("workspace_id", f.ws)
                .containsEntry("target_type", "WORKSPACE")
                .containsEntry("target_id", f.ws);
        Map<String, Object> mine = jdbc.sql("""
                        select workspace_id, target_type, target_id from audit_events
                        where action = 'data.exported' and actor_user_id = :bia""").param("bia", f.bia).query().singleRow();
        assertThat(mine).containsEntry("target_type", "USER").containsEntry("target_id", f.bia);
        assertThat(mine.get("workspace_id")).isNull();
    }

    @Test
    void exportIsRateLimited() {
        Family f = family();

        for (int i = 0; i < 6; i++) {
            assertThat(exportMine(f.bia)).hasStatusOk();
        }
        for (int i = 0; i < 4; i++) {
            assertThat(export("/v1/me/export", TestUsers.signedIn(f.bia, null), f.bia))
                    .hasStatusOk();
        }
        MvcTestResult limited = exportMine(f.bia);

        assertThat(limited).hasStatus(429);
        assertThat(limited).bodyJson().extractingPath("$.code").isEqualTo("rate.limited");
        assertThat(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertThat(exportMine(f.ana)).hasStatusOk();
    }

    @Test
    void familyExportSharesTheBucketOfTheOwner() {
        Family f = family();

        for (int i = 0; i < 5; i++) {
            assertThat(exportMine(f.ana)).hasStatusOk();
            assertThat(exportFamily(f.ana, f.ws)).hasStatusOk();
        }

        assertThat(exportFamily(f.ana, f.ws)).hasStatus(429);
    }

    // --- data ---

    private record Family(UUID ana, UUID bia, UUID ws) {}

    /**
     * Ana owns "Família Teste" with Bia as an adult, and Bia once belonged to "Familia Antiga". Ana recorded one
     * expense for herself, one for Bia, a big grocery run, a transfer, a formula-looking description and a deleted one.
     */
    private Family family() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID cris = users.create("Cris Teste", "cris@example.com");

        UUID oldWs = workspaces.createFor(cris);
        jdbc.sql("update workspaces set name = 'Familia Antiga' where id = :id")
                .param("id", oldWs)
                .update();
        UUID biaOld = workspaces.addMember(oldWs, bia, Role.ADULT, "Bibi");
        UUID oldCategory = ledger.categoryOf(oldWs, "EXPENSE", "Outros");
        ledger.expense(oldWs, biaOld, oldCategory, "7.00", LocalDate.of(2026, 1, 5));
        jdbc.sql("update transactions set description = 'Mercado antigo' where workspace_id = :ws")
                .param("ws", oldWs)
                .update();
        jdbc.sql("update memberships set status = 'REMOVED' where id = :id")
                .param("id", biaOld)
                .update();

        UUID ws = workspaces.createFor(ana);
        UUID anaMember = workspaces.membershipOf(ws, ana);
        jdbc.sql("update memberships set nickname = 'Aninha' where id = :id")
                .param("id", anaMember)
                .update();
        UUID biaMember = workspaces.addMember(ws, bia, Role.ADULT, "Bibi");

        UUID wallet = ledger.walletOf(ws);
        UUID checking = account(ws, "Corrente", "CHECKING", "10.00");
        UUID market = ledger.categoryOf(ws, "EXPENSE", "Mercado");
        // Rename so the CSV line of the brief reads "Mercado" even if the default differs.
        transaction(ws, checking, null, market, "EXPENSE", "1234.56", "Compra grande", anaMember, anaMember, false);
        transaction(ws, checking, wallet, null, "TRANSFER", "100.00", "", anaMember, anaMember, false);
        transaction(ws, checking, null, market, "EXPENSE", "5.00", "So da Dona", anaMember, anaMember, false);
        transaction(ws, checking, null, market, "EXPENSE", "6.00", "Para a Bia", biaMember, biaMember, false);
        transaction(
                ws,
                checking,
                null,
                market,
                "EXPENSE",
                "8.00",
                "Feita pela Dona para a Bia",
                biaMember,
                anaMember,
                false);
        transaction(ws, checking, null, market, "EXPENSE", "9.00", "=1+1", anaMember, anaMember, false);
        transaction(ws, checking, null, market, "EXPENSE", "11.00", "Apagada", anaMember, anaMember, true);
        return new Family(ana, bia, ws);
    }

    private UUID account(UUID ws, String name, String type, String initial) {
        UUID id = TestIds.v7();
        jdbc.sql("""
                        insert into accounts (id, workspace_id, name, type, currency, initial_balance)
                        values (:id, :ws, :name, :type, 'BRL', :initial)""")
                .param("id", id)
                .param("ws", ws)
                .param("name", name)
                .param("type", type)
                .param("initial", new BigDecimal(initial))
                .update();
        return id;
    }

    private void transaction(
            UUID ws,
            UUID account,
            @Nullable UUID toAccount,
            @Nullable UUID category,
            String kind,
            String amount,
            String description,
            UUID member,
            UUID createdBy,
            boolean deleted) {
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, to_account_id,
                             category_id, description, member_id, created_by, deleted_at)
                        values (:id, :ws, :kind, :amount, 'BRL', :on, :account, :toAccount, :category,
                                nullif(:description, ''), :member, :createdBy,
                                case when :deleted then now() end)""")
                .param("id", TestIds.v7())
                .param("ws", ws)
                .param("kind", kind)
                .param("amount", new BigDecimal(amount))
                .param("on", LocalDate.of(2026, 11, 10))
                .param("account", account)
                .param("toAccount", toAccount)
                .param("category", category)
                .param("description", description)
                .param("member", member)
                .param("createdBy", createdBy)
                .param("deleted", deleted)
                .update();
    }

    // --- requests ---

    private MvcTestResult exportMine(UUID user) {
        return export("/v1/me/export", TestUsers.signedIn(user, null), user);
    }

    private MvcTestResult exportFamily(UUID user, UUID ws) {
        return export("/v1/workspaces/" + ws + "/export", TestUsers.signedIn(user, null), user);
    }

    private MvcTestResult export(String uri, RequestPostProcessor session, UUID user) {
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
}

package br.com.liberdadefamilia.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.shared.Money;
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
import java.util.Currency;
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
        assertThat(files).containsOnlyKeys("dados.json", "lancamentos.csv", "atividade.csv", "aportes.csv");
        String json = text(files.get("dados.json"));
        assertThat(JsonPath.<Integer>read(json, "$.formatVersion")).isEqualTo(10);
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
    void myExportHasMyAchievementsFromEveryFamily() {
        Family f = family();
        UUID biaHere = workspaces.membershipOf(f.ws, f.bia);
        UUID biaOld = jdbc.sql("select id from memberships where user_id = :u and workspace_id <> :ws")
                .param("u", f.bia)
                .param("ws", f.ws)
                .query(UUID.class)
                .single();
        UUID oldWs = jdbc.sql("select workspace_id from memberships where id = :id")
                .param("id", biaOld)
                .query(UUID.class)
                .single();
        achievement(f.ws, biaHere, "FIRST_TRANSACTION", "2026-11-10T12:00:00Z", false);
        achievement(f.ws, biaHere, "FIRST_GOAL", "2026-11-09T12:00:00Z", true);
        achievement(oldWs, biaOld, "FIRST_TRANSACTION", "2026-01-05T12:00:00Z", false);
        achievement(f.ws, workspaces.membershipOf(f.ws, f.ana), "FIRST_TRANSACTION", "2026-11-11T12:00:00Z", false);

        String json = text(unzip(exportMine(f.bia)).get("dados.json"));

        assertThat(JsonPath.<List<?>>read(json, "$.achievements")).hasSize(3);
        assertThat(JsonPath.<List<String>>read(json, "$.achievements[*].workspaceId"))
                .containsExactlyInAnyOrder(oldWs.toString(), f.ws.toString(), f.ws.toString());
        assertThat(JsonPath.<List<String>>read(json, "$.achievements[?(@.workspaceId=='" + f.ws + "')].key"))
                .containsExactly("FIRST_GOAL", "FIRST_TRANSACTION");
        assertThat(JsonPath.<List<String>>read(json, "$.achievements[?(@.workspaceId=='" + oldWs + "')].earnedAt"))
                .containsExactly("2026-01-05T12:00:00Z");
        assertThat(JsonPath.<Map<String, Object>>read(json, "$.achievements[0]"))
                .containsOnlyKeys("workspaceId", "key", "earnedAt");
    }

    @Test
    void myExportHasNoOneElsesAchievements() {
        Family f = family();
        achievement(f.ws, workspaces.membershipOf(f.ws, f.ana), "FIRST_TRANSACTION", "2026-11-11T12:00:00Z", false);

        String json = text(unzip(exportMine(f.bia)).get("dados.json"));

        assertThat(JsonPath.<List<?>>read(json, "$.achievements")).isEmpty();
    }

    @Test
    void theFamilyExportDoesNotCarryAchievements() {
        Family f = family();
        achievement(f.ws, workspaces.membershipOf(f.ws, f.ana), "FIRST_TRANSACTION", "2026-11-11T12:00:00Z", false);

        String json = text(unzip(exportFamily(f.ana, f.ws)).get("dados.json"));

        assertThat(json).doesNotContain("achievements");
    }

    private void achievement(UUID ws, UUID membership, String key, String at, boolean silent) {
        jdbc.sql("""
                        insert into achievements (workspace_id, membership_id, achievement_key, earned_at, silent)
                        values (:ws, :m, :key, :at::timestamptz, :silent)""")
                .param("ws", ws)
                .param("m", membership)
                .param("key", key)
                .param("at", at)
                .param("silent", silent)
                .update();
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
                        "﻿familia;data;tipo;valor;moeda;conta;conta_destino;categoria;descricao;quem;criado_em;status;forma_pagamento;tags;projeto\r\n");
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
                .containsOnlyKeys(
                        "dados.json",
                        "lancamentos.csv",
                        "contas.csv",
                        "categorias.csv",
                        "membros.csv",
                        "recorrencias.csv",
                        "tags.csv",
                        "orcamentos.csv",
                        "metas.csv",
                        "aportes.csv",
                        "projetos.csv",
                        "projetos_itens.csv",
                        "projetos_produtos.csv",
                        "projetos_passos.csv",
                        "feira-mercados.csv",
                        "feira-endereco.csv",
                        "feira-produtos.csv",
                        "feira-apelidos.csv",
                        "feira-precos.csv",
                        "feira-notas.csv");
        String csv = text(files.get("lancamentos.csv"));
        assertThat(csv)
                .startsWith(
                        "﻿data;tipo;valor;moeda;conta;conta_destino;categoria;descricao;quem;criado_em;status;forma_pagamento;tags;projeto\r\n");
        assertThat(csv).containsPattern(";Gasto;1234,56;BRL;Corrente;;Mercado;Compra grande;Aninha;");
        assertThat(csv).contains(";Transferência;100,00;BRL;Corrente;Carteira;;;Aninha;");
        assertThat(csv).contains("So da Dona").contains("Para a Bia");
        String accounts = text(files.get("contas.csv"));
        assertThat(accounts).contains("nome;tipo;saldo_inicial;saldo;moeda;titular;arquivada_em;reserva");
        assertThat(accounts)
                .contains("Corrente;Conta corrente;10,00;-1352,56;BRL;;;false")
                .contains("Carteira;Dinheiro;");
        assertThat(text(files.get("categorias.csv")))
                .contains("nome;tipo;categoria_pai;arquivada_em;fixa")
                .contains("Mercado;Gasto");
        assertThat(text(files.get("membros.csv")))
                .contains("apelido;papel;situacao")
                .contains("Aninha;Dono;Ativo")
                .contains("Bibi;Adulto;Ativo");
        String json = text(files.get("dados.json"));
        assertThat(JsonPath.<String>read(json, "$.workspace.name")).isEqualTo("Família Teste");
        assertThat(JsonPath.<List<?>>read(json, "$.members")).hasSize(2);
        assertThat(JsonPath.<Boolean>read(json, "$.modules.planning")).isTrue();
        assertThat(JsonPath.<String>read(json, "$.transactions[0].amount.currency"))
                .isEqualTo("BRL");
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            assertThat(text(file.getValue())).as(file.getKey()).doesNotContain("@example.com");
        }
    }

    @Test
    void familyExportIncludesBilling() {
        Family f = family();
        seedBilling(f.ws, f.ana, 8900, "PAID", true, "chk_secret", "pay_secret");
        UUID otherOwner = users.create("Outra Dona", "outra@example.com");
        UUID otherWs = workspaces.createFor(otherOwner);
        seedBilling(otherWs, otherOwner, 4200, "REFUNDED", false, "chk_other", "pay_other");

        String json = text(unzip(exportFamily(f.ana, f.ws)).get("dados.json"));

        assertThat(JsonPath.<Integer>read(json, "$.formatVersion")).isEqualTo(10);
        assertThat(JsonPath.<List<?>>read(json, "$.orders")).hasSize(1);
        assertThat(JsonPath.<String>read(json, "$.orders[0].status")).isEqualTo("PAID");
        assertThat(JsonPath.<String>read(json, "$.orders[0].amount")).isEqualTo("89.00");
        assertThat(JsonPath.<String>read(json, "$.orders[0].currency")).isEqualTo("BRL");
        assertThat(JsonPath.<Boolean>read(json, "$.plan.founder")).isTrue();
        assertThat(JsonPath.<String>read(json, "$.plan.key")).isEqualTo("FAMILY");
        assertThat(json).doesNotContain("chk_").doesNotContain("pay_");
        assertThat(json).doesNotContain("42.00").doesNotContain("REFUNDED");
    }

    /** One order and the plan it bought, with gateway ids that must never reach the export. */
    private void seedBilling(
            UUID ws, UUID by, long cents, String status, boolean founder, String checkoutId, String paymentId) {
        UUID order = jdbc.sql("""
                        insert into billing_orders (workspace_id, requested_by, product, amount_cents, currency, status,
                                                    asaas_checkout_id, asaas_payment_id, paid_at)
                        values (:ws, :by, 'FOUNDER_2026', :cents, 'BRL', :status, :checkout, :payment,
                                '2026-12-01T12:00:00Z')
                        returning id""")
                .param("ws", ws)
                .param("by", by)
                .param("cents", cents)
                .param("status", status)
                .param("checkout", checkoutId)
                .param("payment", paymentId)
                .query(UUID.class)
                .single();
        String insertPlan = """
                insert into workspace_plans (workspace_id, plan_key, founder, source_order_id, starts_at, ends_at)
                values (:ws, 'FAMILY', :founder, :order, '2026-12-01T12:00:00Z', '2027-12-31T23:59:59Z')""";
        jdbc.sql(insertPlan)
                .param("ws", ws)
                .param("founder", founder)
                .param("order", order)
                .update();
    }

    @Test
    void familyExportWithoutBillingHasNoOrdersAndNoPlan() {
        Family f = family();

        String json = text(unzip(exportFamily(f.ana, f.ws)).get("dados.json"));

        assertThat(JsonPath.<List<?>>read(json, "$.orders")).isEmpty();
        assertThat(JsonPath.<Object>read(json, "$.plan")).isNull();
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

    @Test
    void formatVersion5WithPlanningData() {
        Family f = family();
        UUID anaMember = workspaces.membershipOf(f.ws, f.ana);
        UUID checking = idOf("accounts", f.ws, "Corrente");
        UUID market = idOf("categories", f.ws, "Mercado");
        UUID rule = rule(f.ws, checking, market, anaMember);
        ledger.insertPlanned(f.ws, checking, market, reais("50.00"), LocalDate.of(2026, 12, 5), rule, anaMember);
        UUID paid = transaction(
                f.ws, checking, null, market, "EXPENSE", "21.00", "Mensalidade", anaMember, anaMember, false);
        jdbc.sql("update transactions set payment_method = 'PIX' where id = :id")
                .param("id", paid)
                .update();
        tag(f.ws, "Escola", anaMember, paid);

        String json = text(unzip(exportFamily(f.ana, f.ws)).get("dados.json"));

        assertThat(JsonPath.<Integer>read(json, "$.formatVersion")).isEqualTo(10);
        assertThat(JsonPath.<String>read(json, "$.workspace.country")).isEqualTo("BR");
        assertThat(JsonPath.<String>read(json, "$.workspace.locale")).isEqualTo("pt-BR");
        assertThat(JsonPath.<List<?>>read(json, "$.recurringRules")).hasSize(1);
        assertThat(JsonPath.<String>read(json, "$.recurringRules[0].frequency")).isEqualTo("MONTHLY");
        assertThat(JsonPath.<List<?>>read(json, "$.tags")).hasSize(1);
        assertThat(JsonPath.<String>read(json, "$.tags[0].name")).isEqualTo("Escola");
        assertThat(JsonPath.<List<String>>read(json, "$.transactions[?(@.description=='Mensalidade')].paymentMethod"))
                .containsExactly("PIX");
        assertThat(JsonPath.<List<List<String>>>read(json, "$.transactions[?(@.description=='Mensalidade')].tags"))
                .containsExactly(List.of("Escola"));
        assertThat(JsonPath.<List<String>>read(json, "$.transactions[?(@.status=='PLANNED')].status"))
                .containsExactly("PLANNED");
        assertThat(JsonPath.<List<String>>read(json, "$.transactions[?(@.status=='PLANNED')].scheduledOn"))
                .containsExactly("2026-12-05");
        assertThat(JsonPath.<List<String>>read(json, "$.transactions[?(@.status=='PLANNED')].recurringRuleId"))
                .containsExactly(rule.toString());
    }

    @Test
    void exportsImportKey() {
        Family f = family();
        UUID anaMember = workspaces.membershipOf(f.ws, f.ana);
        UUID checking = idOf("accounts", f.ws, "Corrente");
        UUID market = idOf("categories", f.ws, "Mercado");
        UUID imported =
                transaction(f.ws, checking, null, market, "EXPENSE", "12.00", "Importada", anaMember, anaMember, false);
        transaction(f.ws, checking, null, market, "EXPENSE", "13.00", "Manual", anaMember, anaMember, false);
        String key = "ab".repeat(32);
        jdbc.sql("update transactions set import_key = :key where id = :id")
                .param("key", key)
                .param("id", imported)
                .update();

        String family = text(unzip(exportFamily(f.ana, f.ws)).get("dados.json"));
        String mine = text(unzip(exportMine(f.ana)).get("dados.json"));

        for (String json : List.of(family, mine)) {
            assertThat(JsonPath.<Integer>read(json, "$.formatVersion")).isEqualTo(10);
            assertThat(JsonPath.<List<Map<String, Object>>>read(json, "$.transactions[?(@.description=='Importada')]"))
                    .singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("importKey", key));
            assertThat(JsonPath.<List<Map<String, Object>>>read(json, "$.transactions[?(@.description=='Manual')]"))
                    .singleElement()
                    .satisfies(row -> assertThat(row.get("importKey")).isNull());
        }
    }

    @Test
    void familyExportV5() {
        Family f = family();
        UUID anaMember = workspaces.membershipOf(f.ws, f.ana);
        UUID market = idOf("categories", f.ws, "Mercado");
        UUID reserve = account(f.ws, "Colchao", "SAVINGS", "500.00");
        jdbc.sql("update accounts set is_reserve = true where id = :id")
                .param("id", reserve)
                .update();
        jdbc.sql("update categories set is_fixed = true where id = :id")
                .param("id", market)
                .update();
        jdbc.sql("update workspaces set manual_monthly_cost = 4500.50 where id = :id")
                .param("id", f.ws)
                .update();
        budget(f.ws, market, "2026-11-01", "1500.5");
        UUID goal = goal(f.ws, "Viagem", "12000", anaMember);
        contribution(f.ws, goal, "250.5", "2026-11-12", anaMember);

        Map<String, byte[]> files = unzip(exportFamily(f.ana, f.ws));

        String json = text(files.get("dados.json"));
        assertThat(JsonPath.<Integer>read(json, "$.formatVersion")).isEqualTo(10);
        assertThat(JsonPath.<Integer>read(json, "$.reserveTargetMonths")).isEqualTo(6);
        assertThat(JsonPath.<Number>read(json, "$.manualMonthlyCost").doubleValue())
                .isEqualTo(4500.5);
        assertThat(JsonPath.<List<?>>read(json, "$.budgets")).hasSize(1);
        assertThat(JsonPath.<List<?>>read(json, "$.goals")).hasSize(1);
        assertThat(JsonPath.<List<?>>read(json, "$.goalContributions")).hasSize(1);
        assertThat(text(files.get("orcamentos.csv")))
                .startsWith("\uFEFFcategoria;mes;limite;moeda\r\n")
                .contains("Mercado;2026-11;1500,50;BRL");
        assertThat(text(files.get("metas.csv")))
                .contains("nome;valor_alvo;moeda;vencimento;icone;conta_vinculada;atingida_em;arquivada_em")
                .contains("Viagem;12000,00;BRL;;savings;;;");
        assertThat(text(files.get("aportes.csv")))
                .contains("meta;data;valor;moeda;quem")
                .contains("Viagem;2026-11-12;250,50;BRL;Aninha");
        assertThat(text(files.get("contas.csv"))).contains("Colchao;Poupança;500,00;500,00;BRL;;;true");
        assertThat(text(files.get("categorias.csv"))).contains("Mercado;Gasto;;;true");
    }

    @Test
    void personalExportHasOwnContributions() {
        Family f = family();
        UUID anaMember = workspaces.membershipOf(f.ws, f.ana);
        UUID biaMember = workspaces.membershipOf(f.ws, f.bia);
        UUID market = idOf("categories", f.ws, "Mercado");
        UUID goal = goal(f.ws, "Casa", "90000", anaMember);
        contribution(f.ws, goal, "100", "2026-11-03", anaMember);
        contribution(f.ws, goal, "40", "2026-11-04", biaMember);
        budget(f.ws, market, "2026-11-01", "800");

        Map<String, byte[]> biaFiles = unzip(exportMine(f.bia));
        Map<String, byte[]> anaFiles = unzip(exportMine(f.ana));

        String biaJson = text(biaFiles.get("dados.json"));
        assertThat(JsonPath.<List<String>>read(biaJson, "$.goalContributions[*].memberId"))
                .containsExactly(biaMember.toString());
        assertThat(JsonPath.<String>read(biaJson, "$.goalContributions[0].goalName"))
                .isEqualTo("Casa");
        assertThat(text(biaFiles.get("aportes.csv")))
                .contains("Casa;2026-11-04;40,00;BRL")
                .doesNotContain("100,00");
        assertThat(text(anaFiles.get("aportes.csv")))
                .contains("Casa;2026-11-03;100,00;BRL")
                .doesNotContain("40,00");
        assertThat(biaJson).doesNotContain("budgets").doesNotContain("reserveTargetMonths");
    }

    @Test
    void moneyColumnsFormatted() {
        Family f = family();
        UUID anaMember = workspaces.membershipOf(f.ws, f.ana);
        UUID market = idOf("categories", f.ws, "Mercado");
        budget(f.ws, market, "2026-12-01", "1234.5");
        UUID goal = goal(f.ws, "Reforma", "99999.9", anaMember);
        contribution(f.ws, goal, "-7.005", "2026-11-12", anaMember);

        Map<String, byte[]> files = unzip(exportFamily(f.ana, f.ws));

        assertThat(text(files.get("orcamentos.csv"))).contains(";1234,50;BRL");
        assertThat(text(files.get("metas.csv"))).contains("Reforma;99999,90;BRL");
        // A negative amount is a number, not a formula: it is not prefixed.
        assertThat(text(files.get("aportes.csv"))).contains("Reforma;2026-11-12;-7,00;BRL");
    }

    @Test
    void csvHasNewColumnsAndFiles() {
        Family f = family();
        UUID anaMember = workspaces.membershipOf(f.ws, f.ana);
        UUID checking = idOf("accounts", f.ws, "Corrente");
        UUID market = idOf("categories", f.ws, "Mercado");
        rule(f.ws, checking, market, anaMember);
        UUID paid = transaction(
                f.ws, checking, null, market, "EXPENSE", "21.00", "Mensalidade", anaMember, anaMember, false);
        jdbc.sql("update transactions set payment_method = 'PIX' where id = :id")
                .param("id", paid)
                .update();
        tag(f.ws, "Escola", anaMember, paid);
        tag(f.ws, "Filhos", anaMember, paid);

        Map<String, byte[]> files = unzip(exportFamily(f.ana, f.ws));

        assertThat(files).containsKeys("recorrencias.csv", "tags.csv");
        assertThat(text(files.get("lancamentos.csv")))
                .contains("criado_em;status;forma_pagamento;tags;projeto\r\n")
                .containsPattern(";Mensalidade;Aninha;[^;]*;Confirmado;Pix;\"?Escola; Filhos\"?;\r\n");
        assertThat(text(files.get("recorrencias.csv")))
                .contains(
                        "tipo;valor;moeda;conta;conta_destino;categoria;forma_pagamento;descricao;frequencia;intervalo;inicio;fim;arquivada_em")
                .contains("Gasto;30,00;BRL;Corrente;;Mercado;;Aluguel;Mensal;1;2026-12-01;;");
        assertThat(text(files.get("tags.csv")))
                .contains("nome;arquivada_em")
                .contains("Escola;")
                .contains("Filhos;");
    }

    @Test
    void skippedPlannedStaysOut() {
        Family f = family();
        UUID anaMember = workspaces.membershipOf(f.ws, f.ana);
        UUID checking = idOf("accounts", f.ws, "Corrente");
        UUID market = idOf("categories", f.ws, "Mercado");
        UUID rule = rule(f.ws, checking, market, anaMember);
        UUID skipped = ledger.insertPlanned(
                f.ws, checking, market, reais("77.00"), LocalDate.of(2026, 12, 5), rule, anaMember);
        jdbc.sql("update transactions set deleted_at = now() where id = :id")
                .param("id", skipped)
                .update();

        Map<String, byte[]> files = unzip(exportFamily(f.ana, f.ws));

        String json = text(files.get("dados.json"));
        assertThat(json).doesNotContain(skipped.toString());
        assertThat(JsonPath.<List<?>>read(json, "$.transactions[?(@.status=='PLANNED')]"))
                .isEmpty();
        assertThat(text(files.get("lancamentos.csv"))).doesNotContain("77,00");
    }

    @Test
    void tagsCreatedByThePerson() {
        Family f = family();
        UUID anaMember = workspaces.membershipOf(f.ws, f.ana);
        UUID biaMember = workspaces.membershipOf(f.ws, f.bia);
        tag(f.ws, "DaAdulta", biaMember, null);
        tag(f.ws, "DoDono", anaMember, null);

        String json = text(unzip(exportMine(f.bia)).get("dados.json"));

        assertThat(JsonPath.<List<String>>read(json, "$.tags[*].name")).containsExactly("DaAdulta");
    }

    @Test
    void transferRuleHasDestinationColumn() {
        Family f = family();
        UUID anaMember = workspaces.membershipOf(f.ws, f.ana);
        UUID checking = idOf("accounts", f.ws, "Corrente");
        UUID savings = jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency)
                        values (:ws, 'Reserva', 'SAVINGS', 'BRL') returning id""").param("ws", f.ws).query(UUID.class).single();
        jdbc.sql("""
                        insert into recurring_rules (workspace_id, kind, amount, currency, account_id, to_account_id,
                                                     description, frequency, "interval", start_on, created_by)
                        values (:ws, 'TRANSFER', 100.00, 'BRL', :account, :to, 'Mesada', 'MONTHLY', 1,
                                '2026-12-01', :by)""")
                .param("ws", f.ws)
                .param("account", checking)
                .param("to", savings)
                .param("by", anaMember)
                .update();

        String csv = text(unzip(exportFamily(f.ana, f.ws)).get("recorrencias.csv"));

        assertThat(csv).contains("Transferência;100,00;BRL;Corrente;Reserva;;;Mesada;");
    }

    @Test
    void rulesCreatedByThePerson() {
        Family f = family();
        UUID anaMember = workspaces.membershipOf(f.ws, f.ana);
        UUID biaMember = workspaces.membershipOf(f.ws, f.bia);
        UUID checking = idOf("accounts", f.ws, "Corrente");
        UUID market = idOf("categories", f.ws, "Mercado");
        UUID biasRule = rule(f.ws, checking, market, biaMember);
        rule(f.ws, checking, market, anaMember);

        String json = text(unzip(exportMine(f.bia)).get("dados.json"));

        assertThat(JsonPath.<List<String>>read(json, "$.recurringRules[*].id")).containsExactly(biasRule.toString());
        assertThat(JsonPath.<String>read(json, "$.recurringRules[0].frequency")).isEqualTo("MONTHLY");
        assertThat(JsonPath.<String>read(json, "$.recurringRules[0].familyName"))
                .isNotEmpty();
    }

    private static Money reais(String amount) {
        return Money.of(new BigDecimal(amount), Currency.getInstance("BRL"));
    }

    private UUID idOf(String table, UUID ws, String name) {
        return jdbc.sql("select id from " + table + " where workspace_id = :ws and name = :name")
                .param("ws", ws)
                .param("name", name)
                .query(UUID.class)
                .single();
    }

    private UUID rule(UUID ws, UUID account, UUID category, UUID createdBy) {
        return jdbc.sql("""
                        insert into recurring_rules (workspace_id, kind, amount, currency, account_id, category_id,
                                                     description, frequency, "interval", start_on, created_by)
                        values (:ws, 'EXPENSE', 30.00, 'BRL', :account, :category, 'Aluguel', 'MONTHLY', 1,
                                '2026-12-01', :by)
                        returning id""")
                .param("ws", ws)
                .param("account", account)
                .param("category", category)
                .param("by", createdBy)
                .query(UUID.class)
                .single();
    }

    /** Creates the tag for the membership and, when a transaction is given, puts it on it. */
    private void tag(UUID ws, String name, UUID createdBy, @Nullable UUID transaction) {
        UUID id = jdbc.sql("insert into tags (workspace_id, name, created_by) values (:ws, :name, :by) returning id")
                .param("ws", ws)
                .param("name", name)
                .param("by", createdBy)
                .query(UUID.class)
                .single();
        if (transaction != null) {
            jdbc.sql("insert into transaction_tags (workspace_id, transaction_id, tag_id) values (:ws, :tx, :tag)")
                    .param("ws", ws)
                    .param("tx", transaction)
                    .param("tag", id)
                    .update();
        }
    }

    // --- data ---

    private void budget(UUID ws, UUID category, String month, String limit) {
        jdbc.sql("""
                        insert into budgets (workspace_id, category_id, month, limit_amount, currency)
                        values (:ws, :category, :month::date, :limit, 'BRL')""")
                .param("ws", ws)
                .param("category", category)
                .param("month", month)
                .param("limit", new BigDecimal(limit))
                .update();
    }

    private UUID goal(UUID ws, String name, String target, UUID createdBy) {
        return jdbc.sql("""
                        insert into goals (workspace_id, name, target_amount, currency, icon, created_by)
                        values (:ws, :name, :target, 'BRL', 'savings', :by) returning id""")
                .param("ws", ws)
                .param("name", name)
                .param("target", new BigDecimal(target))
                .param("by", createdBy)
                .query(UUID.class)
                .single();
    }

    private void contribution(UUID ws, UUID goal, String amount, String on, UUID member) {
        jdbc.sql("""
                        insert into goal_contributions (workspace_id, goal_id, amount, occurred_on, member_id)
                        values (:ws, :goal, :amount, :on::date, :member)""")
                .param("ws", ws)
                .param("goal", goal)
                .param("amount", new BigDecimal(amount))
                .param("on", on)
                .param("member", member)
                .update();
    }

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

    private UUID transaction(
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
        UUID id = TestIds.v7();
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, to_account_id,
                             category_id, description, member_id, created_by, deleted_at)
                        values (:id, :ws, :kind, :amount, 'BRL', :on, :account, :toAccount, :category,
                                nullif(:description, ''), :member, :createdBy,
                                case when :deleted then now() end)""")
                .param("id", id)
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
        return id;
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

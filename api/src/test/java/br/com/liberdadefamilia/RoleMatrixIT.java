package br.com.liberdadefamilia;

import static br.com.liberdadefamilia.workspace.Role.ADULT;
import static br.com.liberdadefamilia.workspace.Role.OWNER;
import static br.com.liberdadefamilia.workspace.Role.VIEWER;
import static br.com.liberdadefamilia.workspace.Role.YOUTH;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * US-10, route by route: what each role gets on every family route. Each row runs against a family of its own, with
 * one member of each role, so that a write in one row never changes what another sees. A row that is turned down also
 * checks that the family's data did not change.
 *
 * <p>Later tasks add their routes as new row groups in {@link #rows()}; {@link #everyFamilyRouteHasEveryRole} fails
 * until a new family route has a row for each role.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoleMatrixIT {

    private static final String W = "/v1/workspaces/{ws}";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    /** What a row expects: a 2xx, or the status and code of the refusal. */
    enum Outcome {
        OK(0, null),
        FORBIDDEN(403, "auth.forbidden"),
        NOT_FOUND(404, "transaction.not-found"),
        REJECTED(422, "transaction.account-not-found");

        final int status;
        final @Nullable String code;

        Outcome(int status, @Nullable String code) {
            this.status = status;
            this.code = code;
        }
    }

    /**
     * One call: the route as mapped (method and pattern), the path and body with the family's ids as placeholders, the
     * role that calls, and the outcome.
     */
    record Row(
            Role role, String route, String path, @Nullable String body, Outcome expected) {

        String method() {
            return route.substring(0, route.indexOf(' '));
        }

        @Override
        public String toString() {
            return role + " " + method() + " " + path + " -> " + expected;
        }
    }

    // --- the matrix ---

    /** Every row. Later tasks add their groups here. */
    static Stream<Row> rows() {
        return Stream.of(
                        accountRows(),
                        dashboardRows(),
                        categoryRows(),
                        transactionRows(),
                        plannedRows(),
                        tagRows(),
                        recurringRuleRows(),
                        budgetRows(),
                        goalRows(),
                        memberRows(),
                        invitationRows(),
                        workspaceRows(),
                        moduleRows(),
                        exportRows(),
                        deletionRows(),
                        billingRows())
                .flatMap(group -> group);
    }

    static Stream<Row> accountRows() {
        return Stream.of(
                        each(
                                "GET " + W + "/accounts",
                                W + "/accounts",
                                null,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK),
                        ownerAndAdult("POST " + W + "/accounts", W + "/accounts", """
                                {"name":"Nova","type":"SAVINGS","initialBalance":{"amount":"0.00","currency":"BRL"}}"""),
                        ownerAndAdult(
                                "PATCH " + W + "/accounts/{id}", W + "/accounts/{corrente}", "{\"name\":\"Outra\"}"),
                        ownerAndAdult(
                                "PUT " + W + "/accounts/{id}/owner",
                                W + "/accounts/{corrente}/owner",
                                "{\"membershipId\":null}"),
                        // The youth's own wallet is no exception: only adults change accounts.
                        Stream.of(new Row(
                                YOUTH,
                                "PUT " + W + "/accounts/{id}/owner",
                                W + "/accounts/{wallet}/owner",
                                "{\"membershipId\":null}",
                                Outcome.FORBIDDEN)),
                        ownerAndAdult("POST " + W + "/accounts/{id}/archive", W + "/accounts/{corrente}/archive", null),
                        ownerAndAdult(
                                "POST " + W + "/accounts/{id}/unarchive",
                                W + "/accounts/{archivedAccount}/unarchive",
                                null))
                .flatMap(group -> group);
    }

    static Stream<Row> dashboardRows() {
        return each(
                "GET " + W + "/dashboard",
                W + "/dashboard?month=2026-11",
                null,
                Outcome.OK,
                Outcome.OK,
                Outcome.OK,
                Outcome.OK);
    }

    static Stream<Row> tagRows() {
        return Stream.of(
                        each("GET " + W + "/tags", W + "/tags", null, Outcome.OK, Outcome.OK, Outcome.OK, Outcome.OK),
                        ownerAndAdult("PATCH " + W + "/tags/{id}", W + "/tags/{feira}", "{\"name\":\"Outra\"}"),
                        ownerAndAdult("POST " + W + "/tags/{id}/archive", W + "/tags/{feira}/archive", null))
                .flatMap(group -> group);
    }

    static Stream<Row> recurringRuleRows() {
        String rule = """
                {"kind":"EXPENSE","amount":{"amount":"30.00","currency":"BRL"},"accountId":"{corrente}",
                 "categoryId":"{mercado}","frequency":"MONTHLY","interval":1,"startOn":"2026-11-05","endOn":null}""";
        String edit = rule.replace("{\"kind\"", "{\"version\":0,\"kind\"");
        return Stream.of(
                        each(
                                "GET " + W + "/recurring-rules",
                                W + "/recurring-rules",
                                null,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK),
                        ownerAndAdult("POST " + W + "/recurring-rules", W + "/recurring-rules", rule),
                        ownerAndAdult("PATCH " + W + "/recurring-rules/{id}", W + "/recurring-rules/{rule}", edit),
                        ownerAndAdult(
                                "POST " + W + "/recurring-rules/{id}/archive",
                                W + "/recurring-rules/{rule}/archive",
                                null))
                .flatMap(group -> group);
    }

    static Stream<Row> budgetRows() {
        String put = "{\"limitAmount\":{\"amount\":\"100.00\",\"currency\":\"BRL\"},\"version\":null}";
        return Stream.of(
                        each(
                                "GET " + W + "/budgets",
                                W + "/budgets?month=2026-11",
                                null,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.FORBIDDEN,
                                Outcome.OK),
                        ownerAndAdult(
                                "PUT " + W + "/budgets/{month}/{categoryId}", W + "/budgets/2026-11/{mercado}", put),
                        ownerAndAdult(
                                "DELETE " + W + "/budgets/{month}/{categoryId}",
                                W + "/budgets/2026-11/{mercado}",
                                null),
                        ownerAndAdult(
                                "POST " + W + "/budgets/{month}/copy-previous",
                                W + "/budgets/2026-11/copy-previous",
                                null))
                .flatMap(group -> group);
    }

    static Stream<Row> goalRows() {
        String create = "{\"name\":\"Nova\",\"targetAmount\":{\"amount\":\"100.00\",\"currency\":\"BRL\"},"
                + "\"icon\":\"gift\"}";
        String contribution = "{\"amount\":{\"amount\":\"10.00\",\"currency\":\"BRL\"},\"occurredOn\":\"2026-11-02\"}";
        return Stream.of(
                        each("GET " + W + "/goals", W + "/goals", null, Outcome.OK, Outcome.OK, Outcome.OK, Outcome.OK),
                        ownerAndAdult("POST " + W + "/goals", W + "/goals", create),
                        ownerAndAdult(
                                "PATCH " + W + "/goals/{id}",
                                W + "/goals/{goal}",
                                "{\"name\":\"Outra\",\"version\":0}"),
                        ownerAndAdult("POST " + W + "/goals/{id}/archive", W + "/goals/{goal}/archive", null),
                        ownerAndAdult(
                                "POST " + W + "/goals/{id}/contributions",
                                W + "/goals/{goal}/contributions",
                                contribution),
                        each(
                                "GET " + W + "/goals/{id}/contributions",
                                W + "/goals/{goal}/contributions",
                                null,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK))
                .flatMap(group -> group);
    }

    static Stream<Row> categoryRows() {
        return Stream.of(
                        each(
                                "GET " + W + "/categories",
                                W + "/categories?kind=EXPENSE",
                                null,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK),
                        ownerAndAdult(
                                "POST " + W + "/categories",
                                W + "/categories",
                                "{\"name\":\"Nova\",\"kind\":\"EXPENSE\"}"),
                        ownerAndAdult(
                                "PATCH " + W + "/categories/{id}", W + "/categories/{mercado}", "{\"name\":\"Outra\"}"),
                        ownerAndAdult(
                                "POST " + W + "/categories/{id}/archive", W + "/categories/{mercado}/archive", null),
                        ownerAndAdult(
                                "POST " + W + "/categories/{id}/unarchive",
                                W + "/categories/{archivedCategory}/unarchive",
                                null))
                .flatMap(group -> group);
    }

    /**
     * The adults and the viewer act on a family expense in the "Corrente"; the youth on what is his, in his wallet,
     * and then on what is not, which answers as if it did not exist.
     */
    static Stream<Row> transactionRows() {
        String list = "GET " + W + "/transactions";
        String one = "GET " + W + "/transactions/{id}";
        String record = "POST " + W + "/transactions";
        String edit = "PATCH " + W + "/transactions/{id}";
        String delete = "DELETE " + W + "/transactions/{id}";
        String restore = "POST " + W + "/transactions/{id}/restore";
        return Stream.of(
                        each(
                                list,
                                W + "/transactions?month=2026-11",
                                null,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK),
                        notYouth(one, W + "/transactions/{familyExpense}", null, Outcome.OK),
                        Stream.of(
                                new Row(YOUTH, one, W + "/transactions/{allowance}", null, Outcome.OK),
                                new Row(YOUTH, one, W + "/transactions/{familyExpense}", null, Outcome.NOT_FOUND),
                                new Row(YOUTH, one, W + "/transactions/{familyDeleted}", null, Outcome.NOT_FOUND),
                                new Row(YOUTH, one, W + "/transactions/{youthOnFamily}", null, Outcome.NOT_FOUND),
                                new Row(YOUTH, one, W + "/transactions/{youthTransferOut}", null, Outcome.OK)),
                        notYouth(record, W + "/transactions", expense("{corrente}"), Outcome.FORBIDDEN),
                        Stream.of(
                                new Row(YOUTH, record, W + "/transactions", expense("{wallet}"), Outcome.OK),
                                new Row(YOUTH, record, W + "/transactions", expense("{corrente}"), Outcome.REJECTED),
                                new Row(
                                        YOUTH,
                                        record,
                                        W + "/transactions",
                                        transfer("{wallet}", "{corrente}"),
                                        Outcome.REJECTED),
                                new Row(
                                        YOUTH,
                                        record,
                                        W + "/transactions",
                                        transfer("{corrente}", "{wallet}"),
                                        Outcome.REJECTED)),
                        notYouth(edit, W + "/transactions/{familyExpense}", edit("{corrente}"), Outcome.FORBIDDEN),
                        Stream.of(
                                new Row(YOUTH, edit, W + "/transactions/{youthOwn}", edit("{wallet}"), Outcome.OK),
                                new Row(
                                        YOUTH,
                                        edit,
                                        W + "/transactions/{familyExpense}",
                                        edit("{corrente}"),
                                        Outcome.NOT_FOUND),
                                new Row(
                                        YOUTH,
                                        edit,
                                        W + "/transactions/{allowance}",
                                        edit("{wallet}"),
                                        Outcome.NOT_FOUND),
                                new Row(
                                        YOUTH,
                                        edit,
                                        W + "/transactions/{adultInWallet}",
                                        edit("{wallet}"),
                                        Outcome.NOT_FOUND),
                                new Row(
                                        YOUTH,
                                        edit,
                                        W + "/transactions/{youthOnFamily}",
                                        edit("{wallet}"),
                                        Outcome.NOT_FOUND),
                                new Row(
                                        YOUTH,
                                        edit,
                                        W + "/transactions/{youthTransferOut}",
                                        edit("{wallet}"),
                                        Outcome.NOT_FOUND)),
                        notYouth(delete, W + "/transactions/{familyExpense}", null, Outcome.FORBIDDEN),
                        Stream.of(
                                new Row(YOUTH, delete, W + "/transactions/{youthOwn}", null, Outcome.OK),
                                new Row(YOUTH, delete, W + "/transactions/{familyExpense}", null, Outcome.NOT_FOUND),
                                new Row(YOUTH, delete, W + "/transactions/{allowance}", null, Outcome.NOT_FOUND),
                                new Row(YOUTH, delete, W + "/transactions/{adultInWallet}", null, Outcome.NOT_FOUND),
                                new Row(YOUTH, delete, W + "/transactions/{youthOnFamily}", null, Outcome.NOT_FOUND),
                                new Row(
                                        YOUTH,
                                        delete,
                                        W + "/transactions/{youthTransferOut}",
                                        null,
                                        Outcome.NOT_FOUND)),
                        notYouth(restore, W + "/transactions/{familyDeleted}/restore", null, Outcome.FORBIDDEN),
                        Stream.of(
                                new Row(YOUTH, restore, W + "/transactions/{youthDeleted}/restore", null, Outcome.OK),
                                new Row(
                                        YOUTH,
                                        restore,
                                        W + "/transactions/{familyDeleted}/restore",
                                        null,
                                        Outcome.NOT_FOUND),
                                new Row(
                                        YOUTH,
                                        restore,
                                        W + "/transactions/{allowance}/restore",
                                        null,
                                        Outcome.NOT_FOUND),
                                new Row(
                                        YOUTH,
                                        restore,
                                        W + "/transactions/{youthOnFamilyDeleted}/restore",
                                        null,
                                        Outcome.NOT_FOUND),
                                new Row(
                                        YOUTH,
                                        restore,
                                        W + "/transactions/{youthTransferOutDeleted}/restore",
                                        null,
                                        Outcome.NOT_FOUND)))
                .flatMap(group -> group);
    }

    /**
     * Confirming and skipping a planned transaction: the adults act on one in the "Corrente", the youth on one in his
     * wallet, and then on one that is not his, which answers as if it did not exist.
     */
    static Stream<Row> plannedRows() {
        String confirm = "POST " + W + "/transactions/{id}/confirm";
        String skip = "POST " + W + "/transactions/{id}/skip";
        String version = "{\"version\":0}";
        return Stream.of(
                        notYouth(confirm, W + "/transactions/{plannedFamily}/confirm", version, Outcome.FORBIDDEN),
                        Stream.of(
                                new Row(
                                        YOUTH,
                                        confirm,
                                        W + "/transactions/{plannedYouth}/confirm",
                                        version,
                                        Outcome.OK),
                                new Row(
                                        YOUTH,
                                        confirm,
                                        W + "/transactions/{plannedFamily}/confirm",
                                        version,
                                        Outcome.NOT_FOUND)),
                        notYouth(skip, W + "/transactions/{plannedFamily}/skip", version, Outcome.FORBIDDEN),
                        Stream.of(
                                new Row(YOUTH, skip, W + "/transactions/{plannedYouth}/skip", version, Outcome.OK),
                                new Row(
                                        YOUTH,
                                        skip,
                                        W + "/transactions/{plannedFamily}/skip",
                                        version,
                                        Outcome.NOT_FOUND)))
                .flatMap(group -> group);
    }

    static Stream<Row> memberRows() {
        return Stream.of(
                        each(
                                "GET " + W + "/members",
                                W + "/members",
                                null,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK),
                        ownerOnly("PATCH " + W + "/members/{id}", W + "/members/{target}", "{\"nickname\":\"Tia\"}"),
                        ownerOnly("DELETE " + W + "/members/{id}", W + "/members/{target}", null),
                        ownerAndAdult("POST " + W + "/members/{id}/recovery", W + "/members/{target}/recovery", null))
                .flatMap(group -> group);
    }

    static Stream<Row> invitationRows() {
        return Stream.of(
                        ownerAndAdult(
                                "POST " + W + "/invitations",
                                W + "/invitations",
                                "{\"role\":\"VIEWER\",\"nickname\":\"Vovo\"}"),
                        ownerAndAdult("GET " + W + "/invitations", W + "/invitations", null),
                        ownerAndAdult("DELETE " + W + "/invitations/{id}", W + "/invitations/{invitation}", null))
                .flatMap(group -> group);
    }

    static Stream<Row> workspaceRows() {
        return ownerOnly("PATCH " + W, W, "{\"name\":\"Família Nova\"}");
    }

    static Stream<Row> moduleRows() {
        return Stream.of(
                        each(
                                "GET " + W + "/modules",
                                W + "/modules",
                                null,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK),
                        ownerAndAdult("PUT " + W + "/modules/{key}", W + "/modules/planning", "{\"enabled\":true}"))
                .flatMap(group -> group);
    }

    static Stream<Row> billingRows() {
        return Stream.of(
                        ownerOnly("POST " + W + "/billing/checkout", W + "/billing/checkout", null),
                        ownerOnly(
                                "POST " + W + "/billing/orders/{id}/refund",
                                W + "/billing/orders/{paidOrder}/refund",
                                null),
                        each(
                                "GET " + W + "/billing/plan",
                                W + "/billing/plan",
                                null,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK),
                        ownerOnly(
                                "PUT " + W + "/billing/plan/theme", W + "/billing/plan/theme", "{\"theme\":\"OCEAN\"}"),
                        each(
                                "POST " + W + "/billing/orders/{id}/refresh",
                                W + "/billing/orders/{order}/refresh",
                                null,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK,
                                Outcome.OK))
                .flatMap(group -> group);
    }

    static Stream<Row> exportRows() {
        return ownerOnly("POST " + W + "/export", W + "/export", null);
    }

    static Stream<Row> deletionRows() {
        return Stream.of(
                        ownerOnly("POST " + W + "/deletion", W + "/deletion", null),
                        ownerOnly("DELETE " + W + "/deletion", W + "/deletion", null))
                .flatMap(group -> group);
    }

    // --- row builders ---

    private static Stream<Row> each(
            String route,
            String path,
            @Nullable String body,
            Outcome owner,
            Outcome adult,
            Outcome youth,
            Outcome viewer) {
        return Stream.of(
                new Row(OWNER, route, path, body, owner),
                new Row(ADULT, route, path, body, adult),
                new Row(YOUTH, route, path, body, youth),
                new Row(VIEWER, route, path, body, viewer));
    }

    private static Stream<Row> ownerAndAdult(String route, String path, @Nullable String body) {
        return each(route, path, body, Outcome.OK, Outcome.OK, Outcome.FORBIDDEN, Outcome.FORBIDDEN);
    }

    private static Stream<Row> ownerOnly(String route, String path, @Nullable String body) {
        return each(route, path, body, Outcome.OK, Outcome.FORBIDDEN, Outcome.FORBIDDEN, Outcome.FORBIDDEN);
    }

    /** The rows of OWNER, ADULT and VIEWER; YOUTH comes in rows of its own. */
    private static Stream<Row> notYouth(String route, String path, @Nullable String body, Outcome viewer) {
        return Stream.of(
                new Row(OWNER, route, path, body, Outcome.OK),
                new Row(ADULT, route, path, body, Outcome.OK),
                new Row(VIEWER, route, path, body, viewer));
    }

    private static String expense(String account) {
        return """
                {"id":"{newId}","kind":"EXPENSE","amount":{"amount":"10.00","currency":"BRL"},"occurredOn":"2026-11-03",
                 "accountId":"%s","categoryId":"{mercado}"}""".formatted(account);
    }

    private static String transfer(String from, String to) {
        return """
                {"id":"{newId}","kind":"TRANSFER","amount":{"amount":"10.00","currency":"BRL"},"occurredOn":"2026-11-03",
                 "accountId":"%s","toAccountId":"%s"}""".formatted(from, to);
    }

    private static String edit(String account) {
        return """
                {"version":0,"kind":"EXPENSE","amount":{"amount":"20.00","currency":"BRL"},"occurredOn":"2026-11-03",
                 "accountId":"%s","categoryId":"{mercado}"}""".formatted(account);
    }

    // --- the tests ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void roleGetsWhatTheMatrixSays(Row row) {
        Family family = family();
        runningPlanFor(family, row);
        String before = family.snapshot();

        MvcTestResult result = call(family, row);

        int status = result.getResponse().getStatus();
        if (row.expected() == Outcome.OK) {
            assertThat(status).as("%s: %s", row, body(result)).isBetween(200, 299);
            return;
        }
        assertThat(status).as("%s: %s", row, body(result)).isEqualTo(row.expected().status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(row.expected().code);
        assertThat(family.snapshot()).as("the family's data after %s", row).isEqualTo(before);
    }

    /**
     * Every route under {@code /v1/workspaces/{ws}} has a row for each of the four roles, and every row a route. A
     * mapping with no HTTP method answers every method, and is listed as {@code * pattern}, which no row matches.
     */
    @Test
    void everyFamilyRouteHasEveryRole() {
        Set<String> mapped = new TreeSet<>();
        mappings.getHandlerMethods()
                .keySet()
                .forEach(info -> info.getPatternValues().stream()
                        .filter(pattern -> pattern.equals(W) || pattern.startsWith(W + "/"))
                        .forEach(pattern -> {
                            var methods = info.getMethodsCondition().getMethods();
                            if (methods.isEmpty()) {
                                mapped.add("* " + pattern);
                            }
                            methods.forEach(method -> mapped.add(method.name() + " " + pattern));
                        }));
        Map<String, Set<Role>> covered = new TreeMap<>();
        rows().forEach(row -> covered.computeIfAbsent(row.route(), route -> EnumSet.noneOf(Role.class))
                .add(row.role()));

        assertThat(mapped).hasSizeGreaterThanOrEqualTo(25);
        assertThat(covered.keySet()).isEqualTo(mapped);
        assertThat(covered)
                .allSatisfy((route, roles) -> assertThat(roles).as(route).containsExactlyInAnyOrder(Role.values()));
    }

    // --- the family each row runs against ---

    /**
     * The routes that need a running plan get one before the snapshot is taken: the paid order is its origin, as it is
     * the day after the payment. The shared family has none, since a plan makes the owner's checkout a 409.
     */
    private void runningPlanFor(Family family, Row row) {
        if (row.path().contains("{paidOrder}") || row.path().endsWith("/billing/plan/theme")) {
            jdbc.sql("""
                            insert into workspace_plans (workspace_id, plan_key, founder, source_order_id, starts_at, ends_at)
                            values (:ws, 'FAMILY', true, :order, now() - interval '1 minute', now() + interval '1 year')""")
                    .param("ws", family.ids.get("ws"))
                    .param("order", family.ids.get("paidOrder"))
                    .update();
        }
    }

    private MvcTestResult call(Family family, Row row) {
        String path = family.resolve(row.path());
        UUID caller = family.callers.get(row.role());
        var request = mvc.method(HttpMethod.valueOf(row.method())).uri(path).with(TestUsers.signedIn(caller, null));
        if (!row.method().equals("GET")) {
            request.with(CsrfSession.start(mvc, TestUsers.signedIn(caller, null)));
        }
        if (row.body() != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(family.resolve(row.body()));
        }
        if (row.route().equals("POST " + W + "/transactions")) {
            request.header("Idempotency-Key", family.ids.get("newId").toString());
        }
        return request.exchange();
    }

    /**
     * Dona Ana (OWNER), Bia (ADULT), the youth Leo with his wallet, Vera (VIEWER) and Tia (a second VIEWER, for the
     * member routes to act on), with a family account, an archived account and category, transactions of each kind,
     * and a pending invitation.
     */
    private Family family() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID leo = users.create("Leo Teste", "leo@example.com");
        UUID vera = users.create("Vera Teste", "vera@example.com");
        UUID tia = users.create("Tia Teste", "tia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID anaMembership = workspaces.membershipOf(ws, ana);
        UUID biaMembership = workspaces.addMember(ws, bia, ADULT, "Bia");
        UUID leoMembership = workspaces.addMember(ws, leo, YOUTH, "Leo");
        workspaces.addMember(ws, vera, VIEWER, "Vera");
        UUID tiaMembership = workspaces.addMember(ws, tia, VIEWER, "Tia");

        Map<String, UUID> ids = new HashMap<>();
        ids.put("ws", ws);
        ids.put("newId", TestIds.v7());
        ids.put("target", tiaMembership);
        ids.put("order", jdbc.sql("""
                        insert into billing_orders (workspace_id, product, amount_cents, currency, status)
                        values (:ws, 'FOUNDER_2026', 8900, 'BRL', 'CREATED')
                        returning id""").param("ws", ws).query(UUID.class).single());
        UUID paid = jdbc.sql("""
                        insert into billing_orders (workspace_id, product, amount_cents, currency, status, paid_at)
                        values (:ws, 'FOUNDER_2026', 8900, 'BRL', 'PAID', now() - interval '1 minute')
                        returning id""").param("ws", ws).query(UUID.class).single();
        ids.put("paidOrder", paid);
        ids.put("wallet", account(ws, "Carteira de Leo", "CASH", leoMembership, false));
        ids.put("corrente", account(ws, "Corrente", "CHECKING", null, false));
        ids.put("archivedAccount", account(ws, "Antiga", "CHECKING", null, true));
        ids.put("mercado", jdbc.sql("""
                        select id from categories
                        where workspace_id = :ws and kind = 'EXPENSE' and name = 'Mercado' and parent_id is null""").param("ws", ws).query(UUID.class).single());
        ids.put(
                "archivedCategory",
                jdbc.sql("""
                        insert into categories (workspace_id, kind, name, archived_at)
                        values (:ws, 'EXPENSE', 'Antiga', now())
                        returning id""").param("ws", ws).query(UUID.class).single());
        ids.put(
                "feira",
                jdbc.sql("insert into tags (workspace_id, name) values (:ws, 'feira') returning id")
                        .param("ws", ws)
                        .query(UUID.class)
                        .single());
        ids.put(
                "rule",
                jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, interval,
                             start_on, next_due_on, created_by)
                        values (:ws, 'EXPENSE', 20, 'BRL', :account, :category, 'MONTHLY', 1,
                                date '2026-11-05', date '2026-11-05', :ana)
                        returning id""")
                        .param("ws", ws)
                        .param("account", ids.get("corrente"))
                        .param("category", ids.get("mercado"))
                        .param("ana", anaMembership)
                        .query(UUID.class)
                        .single());
        ids.put("familyExpense", transaction(ws, ids.get("corrente"), null, ids.get("mercado"), anaMembership, false));
        ids.put("familyDeleted", transaction(ws, ids.get("corrente"), null, ids.get("mercado"), anaMembership, true));
        ids.put("youthOwn", transaction(ws, ids.get("wallet"), null, ids.get("mercado"), leoMembership, false));
        ids.put("youthDeleted", transaction(ws, ids.get("wallet"), null, ids.get("mercado"), leoMembership, true));
        ids.put("adultInWallet", transaction(ws, ids.get("wallet"), null, ids.get("mercado"), biaMembership, false));
        ids.put("allowance", transaction(ws, ids.get("corrente"), ids.get("wallet"), null, anaMembership, false));
        // Recorded by Leo but touching the family account (as when he was an adult, or an adult changed it since): his
        // name on it does not make it his to change.
        ids.put("youthOnFamily", transaction(ws, ids.get("corrente"), null, ids.get("mercado"), leoMembership, false));
        ids.put(
                "youthOnFamilyDeleted",
                transaction(ws, ids.get("corrente"), null, ids.get("mercado"), leoMembership, true));
        ids.put(
                "youthTransferOut",
                transaction(ws, ids.get("wallet"), ids.get("corrente"), null, leoMembership, false));
        ids.put(
                "youthTransferOutDeleted",
                transaction(ws, ids.get("wallet"), ids.get("corrente"), null, leoMembership, true));
        ids.put(
                "goal",
                jdbc.sql("""
                        insert into goals (workspace_id, name, target_amount, currency, icon, created_by)
                        values (:ws, 'Viagem', 1000, 'BRL', 'travel', :ana)
                        returning id""")
                        .param("ws", ws)
                        .param("ana", anaMembership)
                        .query(UUID.class)
                        .single());
        ids.put("plannedFamily", planned(ws, ids.get("corrente"), ids.get("mercado"), anaMembership));
        ids.put("plannedYouth", planned(ws, ids.get("wallet"), ids.get("mercado"), anaMembership));
        byte[] tokenHash = new byte[32];
        RANDOM.nextBytes(tokenHash);
        ids.put(
                "invitation",
                jdbc.sql("""
                        insert into invitations (workspace_id, kind, role, nickname, token_hash, created_by, expires_at)
                        values (:ws, 'INVITE', 'VIEWER', 'Vovo', :tokenHash, :ana, now() + interval '7 days')
                        returning id""")
                        .param("ws", ws)
                        .param("tokenHash", tokenHash)
                        .param("ana", ana)
                        .query(UUID.class)
                        .single());

        Map<Role, UUID> callers = new EnumMap<>(Role.class);
        callers.put(OWNER, ana);
        callers.put(ADULT, bia);
        callers.put(YOUTH, leo);
        callers.put(VIEWER, vera);
        return new Family(ws, ids, callers);
    }

    private UUID account(UUID ws, String name, String type, @Nullable UUID owner, boolean archived) {
        return jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency, initial_balance, owner_membership_id,
                                              archived_at)
                        values (:ws, :name, :type, 'BRL', 100, :owner, case when :archived then now() end)
                        returning id""")
                .param("ws", ws)
                .param("name", name)
                .param("type", type)
                .param("owner", owner)
                .param("archived", archived)
                .query(UUID.class)
                .single();
    }

    /** An expense (or, with a destination, a transfer) of 5.00 on 2026-11-02, by and for the membership. */
    private UUID transaction(
            UUID ws, UUID account, @Nullable UUID to, @Nullable UUID category, UUID membership, boolean deleted) {
        UUID id = TestIds.v7();
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, to_account_id,
                             category_id, member_id, created_by, deleted_at)
                        values (:id, :ws, :kind, 5, 'BRL', date '2026-11-02', :account, :to, :category,
                                :membership, :membership, case when :deleted then now() end)""")
                .param("id", id)
                .param("ws", ws)
                .param("kind", to == null ? "EXPENSE" : "TRANSFER")
                .param("account", account)
                .param("to", to)
                .param("category", category)
                .param("membership", membership)
                .param("deleted", deleted)
                .update();
        return id;
    }

    /** A planned expense of 5.00 for 2026-11-04, made by the membership. */
    private UUID planned(UUID ws, UUID account, UUID category, UUID membership) {
        UUID id = TestIds.v7();
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, category_id,
                             member_id, created_by, status, scheduled_on)
                        values (:id, :ws, 'EXPENSE', 5, 'BRL', date '2026-11-04', :account, :category,
                                :membership, :membership, 'PLANNED', date '2026-11-04')""")
                .param("id", id)
                .param("ws", ws)
                .param("account", account)
                .param("category", category)
                .param("membership", membership)
                .update();
        return id;
    }

    private final class Family {

        final UUID ws;
        final Map<String, UUID> ids;
        final Map<Role, UUID> callers;

        Family(UUID ws, Map<String, UUID> ids, Map<Role, UUID> callers) {
            this.ws = ws;
            this.ids = ids;
            this.callers = callers;
        }

        String resolve(String template) {
            Matcher matcher = PLACEHOLDER.matcher(template);
            StringBuilder resolved = new StringBuilder();
            while (matcher.find()) {
                UUID id = ids.get(matcher.group(1));
                assertThat(id).as("placeholder %s", matcher.group()).isNotNull();
                matcher.appendReplacement(resolved, id.toString());
            }
            matcher.appendTail(resolved);
            return resolved.toString();
        }

        /** Everything the family has, as one JSON text: the family row and its members, money and invitations. */
        String snapshot() {
            return jdbc.sql("""
                            select json_build_object(
                                'workspace', (select row_to_json(w) from workspaces w where w.id = :ws),
                                'memberships', (select json_agg(m order by m.id) from memberships m
                                                where m.workspace_id = :ws),
                                'accounts', (select json_agg(a order by a.id) from accounts a where a.workspace_id = :ws),
                                'categories', (select json_agg(c order by c.id) from categories c
                                               where c.workspace_id = :ws),
                                'transactions', (select json_agg(t order by t.id) from transactions t
                                                 where t.workspace_id = :ws),
                                'rules', (select json_agg(r order by r.id) from recurring_rules r
                                          where r.workspace_id = :ws),
                                'goals', (select json_agg(g order by g.id) from goals g where g.workspace_id = :ws),
                                'contributions', (select json_agg(gc order by gc.id) from goal_contributions gc
                                                  where gc.workspace_id = :ws),
                                'invitations', (select json_agg(i order by i.id) from invitations i
                                                where i.workspace_id = :ws),
                                'modules', (select json_agg(x order by x.module_key) from workspace_modules x
                                            where x.workspace_id = :ws),
                                'orders', (select json_agg(o order by o.id) from billing_orders o
                                           where o.workspace_id = :ws),
                                'plan', (select row_to_json(p) from workspace_plans p
                                         where p.workspace_id = :ws))::text""").param("ws", ws).query(String.class).single();
        }
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}

package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.WorkspaceCreated;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** A family is born with its wallet and default categories, or not at all (D29). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LedgerSeedIT.FailingListenerConfig.class})
class LedgerSeedIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    FailAfterSeeding failAfterSeeding;

    @Test
    void newFamilyHasWalletAndCategories() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        UUID workspaceId = workspaces.createFor(userId);
        UUID ownerMembershipId = workspaces.membershipOf(workspaceId, userId);

        var accounts = get(userId, "/v1/workspaces/" + workspaceId + "/accounts");

        assertThat(accounts).hasStatusOk();
        assertThat(accounts).bodyJson().isLenientlyEqualTo("""
                [{"name":"Carteira","type":"CASH","currency":"BRL","balance":{"amount":"0.00","currency":"BRL"}}]""");
        assertThat(jdbc.sql("""
                        select owner_membership_id, initial_balance
                        from accounts
                        where workspace_id = :workspaceId""").param("workspaceId", workspaceId).query().singleRow())
                .containsEntry("owner_membership_id", ownerMembershipId)
                .hasEntrySatisfying(
                        "initial_balance",
                        balance -> assertThat((BigDecimal) balance).isZero());
        assertThat(categories(workspaceId))
                .extracting(row -> row.get("kind"), row -> row.get("name"), row -> row.get("icon"))
                .containsExactly(
                        tuple("EXPENSE", "Mercado", "🛒"),
                        tuple("EXPENSE", "Alimentação fora", "🍽️"),
                        tuple("EXPENSE", "Transporte", "🚌"),
                        tuple("EXPENSE", "Moradia", "🏠"),
                        tuple("EXPENSE", "Saúde", "💊"),
                        tuple("EXPENSE", "Lazer", "🎉"),
                        tuple("EXPENSE", "Contas e serviços", "💡"),
                        tuple("EXPENSE", "Compras", "🛍️"),
                        tuple("EXPENSE", "Educação", "📚"),
                        tuple("EXPENSE", "Pets", "🐾"),
                        tuple("EXPENSE", "Presentes e doações", "🎁"),
                        tuple("EXPENSE", "Impostos e taxas", "🧾"),
                        tuple("EXPENSE", "Outros", "📦"),
                        tuple("INCOME", "Salário", "💼"),
                        tuple("INCOME", "Renda extra", "✨"),
                        tuple("INCOME", "Rendimentos", "📈"),
                        tuple("INCOME", "Reembolsos", "↩️"),
                        tuple("INCOME", "Presentes", "🎁"),
                        tuple("INCOME", "Outros", "📦"));
        assertThat(categories(workspaceId))
                .allSatisfy(row -> assertThat(row)
                        .containsEntry("is_default", true)
                        .containsEntry("parent_id", null)
                        .containsEntry("archived_at", null));
    }

    /** The seed runs inside the creating transaction: when a later listener fails, the family goes with it. */
    @Test
    void failedSeedingLeavesNoFamily() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        failAfterSeeding.arm();
        try {
            var result = mvc.post()
                    .uri("/v1/workspaces")
                    .with(TestUsers.signedIn(userId, null))
                    .with(CsrfSession.start(mvc, TestUsers.signedIn(userId, null)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Família Teste\"}")
                    .exchange();

            assertThat(result).hasStatus(500);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("internal");
        } finally {
            failAfterSeeding.disarm();
        }

        UUID workspaceId = failAfterSeeding.lastWorkspaceId();
        assertThat(failAfterSeeding.categoriesSeen())
                .as("the seed ran first, in the same transaction")
                .isEqualTo(19);
        assertThat(count("select count(*) from memberships where user_id = :id", userId))
                .isZero();
        assertThat(count("select count(*) from workspaces where id = :id", workspaceId))
                .isZero();
        assertThat(count("select count(*) from accounts where workspace_id = :id", workspaceId))
                .isZero();
        assertThat(count("select count(*) from categories where workspace_id = :id", workspaceId))
                .isZero();
    }

    private List<Map<String, Object>> categories(UUID workspaceId) {
        return jdbc.sql("""
                        select kind, name, icon, is_default, parent_id, archived_at
                        from categories
                        where workspace_id = :workspaceId
                        order by kind, sort_order""").param("workspaceId", workspaceId).query().listOfRows();
    }

    private long count(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(Long.class).single();
    }

    private MvcTestResult get(UUID userId, String uri) {
        return mvc.get().uri(uri).with(TestUsers.signedIn(userId, null)).exchange();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingListenerConfig {

        @Bean
        FailAfterSeeding failAfterSeeding(JdbcClient jdbc) {
            return new FailAfterSeeding(jdbc);
        }
    }

    /**
     * Fails family creation after the seed when armed. Registered after the ledger's own listener, it also records
     * what the seed left in the still-open transaction.
     */
    static class FailAfterSeeding {

        private final JdbcClient jdbc;
        private volatile boolean armed;
        private volatile long categoriesSeen = -1;
        private final AtomicReference<UUID> lastWorkspaceId = new AtomicReference<>();

        FailAfterSeeding(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @EventListener
        void on(WorkspaceCreated event) {
            if (!armed) {
                return;
            }
            lastWorkspaceId.set(event.workspaceId());
            categoriesSeen = jdbc.sql("select count(*) from categories where workspace_id = :id")
                    .param("id", event.workspaceId())
                    .query(Long.class)
                    .single();
            throw new IllegalStateException("Simulated failure after seeding");
        }

        void arm() {
            armed = true;
        }

        void disarm() {
            armed = false;
        }

        long categoriesSeen() {
            return categoriesSeen;
        }

        UUID lastWorkspaceId() {
            return lastWorkspaceId.get();
        }
    }
}

package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The getting-started checklist: what {@code GET /v1/me} says about it and the per-member dismiss. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GettingStartedIT {

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

    private UUID userId;
    private UUID workspaceId;
    private UUID membershipId;

    @BeforeEach
    void family() {
        userId = users.create("Ana Teste", "ana@example.com");
        workspaceId = workspaces.createFor(userId);
        membershipId = workspaces.membershipOf(workspaceId, userId);
    }

    @Test
    void freshFamilyHasNoTransactions() {
        var result = me(userId);

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.workspaces[0].hasTransactions")
                .isEqualTo(false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.workspaces[0].gettingStartedDismissedAt")
                .isNull();
    }

    @Test
    void firstTransactionFlipsFlag() {
        addExpense();

        assertThat(me(userId))
                .bodyJson()
                .extractingPath("$.workspaces[0].hasTransactions")
                .isEqualTo(true);
    }

    @Test
    void deletedTransactionDoesNotCount() {
        addExpense();
        jdbc.sql("update transactions set deleted_at = now() where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .update();

        assertThat(me(userId))
                .bodyJson()
                .extractingPath("$.workspaces[0].hasTransactions")
                .isEqualTo(false);
    }

    @Test
    void dismissPersists() {
        assertThat(dismiss(userId, workspaceId)).hasStatus(204);
        Optional<Object> first = dismissedAt(userId);
        assertThat(first).isPresent();

        assertThat(dismiss(userId, workspaceId)).hasStatus(204);

        assertThat(dismissedAt(userId)).isEqualTo(first);
    }

    @Test
    void dismissIsPerMember() {
        UUID otherUser = users.create("Beto Teste", "beto@example.com");
        workspaces.addMember(workspaceId, otherUser, Role.ADULT);

        assertThat(dismiss(userId, workspaceId)).hasStatus(204);

        assertThat(dismissedAt(userId)).isPresent();
        assertThat(dismissedAt(otherUser)).isEmpty();
    }

    @Test
    void dismissOtherFamilyIsNotFound() {
        UUID otherUser = users.create("Beto Teste", "beto@example.com");
        UUID otherWorkspace = workspaces.createFor(otherUser);

        var result = dismiss(userId, otherWorkspace);

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("workspace.not-found");
        assertThat(dismissedAt(otherUser)).isEmpty();
    }

    @Test
    void dismissNeedsCsrf() {
        var result = mvc.post()
                .uri("/v1/me/getting-started/dismiss?workspaceId=" + workspaceId)
                .with(TestUsers.signedIn(userId, null))
                .exchange();

        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("csrf.invalid");
    }

    private void addExpense() {
        ledger.expense(
                workspaceId,
                membershipId,
                ledger.categoryOf(workspaceId, "EXPENSE", "Mercado"),
                "10.00",
                LocalDate.of(2026, 11, 5));
    }

    private MvcTestResult me(UUID user) {
        return mvc.get().uri("/v1/me").with(TestUsers.signedIn(user, null)).exchange();
    }

    private MvcTestResult dismiss(UUID user, UUID workspace) {
        return mvc.post()
                .uri("/v1/me/getting-started/dismiss?workspaceId=" + workspace)
                .with(TestUsers.signedIn(user, null))
                .with(CsrfSession.start(mvc, TestUsers.signedIn(user, null)))
                .exchange();
    }

    private Optional<Object> dismissedAt(UUID user) {
        return jdbc.sql("select getting_started_dismissed_at from memberships where user_id = :userId")
                .param("userId", user)
                .query((rs, row) -> Optional.ofNullable(rs.getObject(1)))
                .single();
    }
}

package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Whether a member sees an account (or the account a transfer enters): the youth's own, never the family's. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountVisibilityIT {

    @Autowired
    AccountVisibility visibility;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    private UUID ws;
    private UUID anaMembership;
    private UUID leoMembership;
    private UUID wallet;
    private UUID corrente;

    @BeforeEach
    void family() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID leo = users.create("Leo Teste", "leo@example.com");
        ws = workspaces.createFor(ana);
        anaMembership = workspaces.membershipOf(ws, ana);
        leoMembership = workspaces.addMember(ws, leo, Role.YOUTH, "Leo");
        wallet = account("Carteira do Leo", leoMembership);
        corrente = account("Corrente", null);
    }

    @Test
    void adultSeesEverything() {
        assertThat(visibility.canSee(ws, anaMembership, Role.ADULT, corrente, null))
                .isTrue();
        assertThat(visibility.canSee(ws, anaMembership, Role.OWNER, wallet, null))
                .isTrue();
        assertThat(visibility.canSee(ws, anaMembership, Role.VIEWER, UUID.randomUUID(), null))
                .isTrue();
    }

    @Test
    void youthSeesOwnAndShared() {
        assertThat(visibility.canSee(ws, leoMembership, Role.YOUTH, wallet, null))
                .isTrue();
        assertThat(visibility.canSee(ws, leoMembership, Role.YOUTH, corrente, null))
                .isFalse();
    }

    @Test
    void youthSeesTransferIntoOwn() {
        assertThat(visibility.canSee(ws, leoMembership, Role.YOUTH, corrente, wallet))
                .isTrue();
        assertThat(visibility.canSee(ws, leoMembership, Role.YOUTH, corrente, corrente))
                .isFalse();
    }

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
}

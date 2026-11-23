package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** What V15 puts in the database: the family's country and locale, and the payment method of a transaction. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MigrationV15IT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    @Test
    void existingFamiliesAreBrazilian() {
        UUID id = jdbc.sql("insert into workspaces (name) values ('Familia Antiga') returning id")
                .query(UUID.class)
                .single();

        var row = jdbc.sql("select country, locale, base_currency from workspaces where id = :id")
                .param("id", id)
                .query()
                .singleRow();

        assertThat(row)
                .containsEntry("country", "BR")
                .containsEntry("locale", "pt-BR")
                .containsEntry("base_currency", "BRL");
    }

    @Test
    void transferWithPaymentMethodViolatesCheck() {
        UUID userId = users.create("Ana Teste", "ana@example.com");
        UUID workspaceId = workspaces.createFor(userId);
        UUID membershipId = workspaces.membershipOf(workspaceId, userId);
        UUID wallet = ledger.walletOf(workspaceId);
        UUID other = jdbc.sql("""
                        insert into accounts (workspace_id, name, type, currency)
                        values (:ws, 'Poupanca', 'SAVINGS', 'BRL') returning id""").param("ws", workspaceId).query(UUID.class).single();

        assertThatThrownBy(() -> jdbc.sql("""
                                insert into transactions
                                    (id, workspace_id, kind, amount, currency, occurred_on,
                                     account_id, to_account_id, member_id, created_by, payment_method)
                                values (:id, :ws, 'TRANSFER', 10, 'BRL', '2026-11-02',
                                        :from, :to, :m, :m, 'PIX')""")
                        .param("id", TestIds.v7())
                        .param("ws", workspaceId)
                        .param("from", wallet)
                        .param("to", other)
                        .param("m", membershipId)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

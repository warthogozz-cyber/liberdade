package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.shared.DomainException;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import br.com.liberdadefamilia.workspace.Role;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The port the planning module calls: check like create, plan once for each occurrence, drop what is still open. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlannedTransactionsIT {

    @Autowired
    PlannedTransactions planned;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    private UUID ws;
    private UUID membership;
    private UUID wallet;
    private UUID mercado;
    private WorkspaceMember actor;

    @BeforeEach
    void family() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        ws = workspaces.createFor(ana);
        membership = workspaces.membershipOf(ws, ana);
        wallet = ledger.walletOf(ws);
        mercado = ledger.categoryOf(ws, "EXPENSE", "Mercado");
        actor = new WorkspaceMember(
                ws, membership, ana, Role.OWNER, ZoneId.of("America/Sao_Paulo"), Currency.getInstance("BRL"));
    }

    @Test
    void planIsIdempotent() {
        UUID rule = rule();
        LocalDate on = LocalDate.of(2026, 12, 5);

        assertThat(planned.plan(ws, rule, on, template(), membership)).isTrue();
        assertThat(planned.plan(ws, rule, on, template(), membership)).isFalse();

        assertThat(count("true")).isOne();
        var row = jdbc.sql("""
                        select status, occurred_on::text as occurred_on, scheduled_on::text as scheduled_on,
                               member_id, created_by, description
                        from transactions
                        where workspace_id = :ws and recurring_rule_id = :rule""").param("ws", ws).param("rule", rule).query().singleRow();
        assertThat(row.get("status")).isEqualTo("PLANNED");
        assertThat(row.get("occurred_on")).isEqualTo("2026-12-05");
        assertThat(row.get("scheduled_on")).isEqualTo("2026-12-05");
        assertThat(row.get("member_id")).isEqualTo(membership);
        assertThat(row.get("created_by")).isEqualTo(membership);
        assertThat(row.get("description")).isEqualTo("Aluguel");
    }

    @Test
    void planSkipsArchivedCategory() {
        UUID rule = rule();
        jdbc.sql("update categories set archived_at = now() where id = :id")
                .param("id", mercado)
                .update();

        assertThat(planned.plan(ws, rule, LocalDate.of(2026, 12, 5), template(), membership))
                .isFalse();

        assertThat(count("true")).isZero();
    }

    @Test
    void planSkipsArchivedAccount() {
        UUID rule = rule();
        jdbc.sql("update accounts set archived_at = now() where id = :id")
                .param("id", wallet)
                .update();

        assertThat(planned.plan(ws, rule, LocalDate.of(2026, 12, 5), template(), membership))
                .isFalse();

        assertThat(count("true")).isZero();
    }

    @Test
    void checkRejectsLikeCreate() {
        PlannedTemplate dollars = new PlannedTemplate(
                TransactionKind.EXPENSE,
                Money.of(new BigDecimal("10.00"), Currency.getInstance("USD")),
                wallet,
                null,
                mercado,
                null,
                null);
        PlannedTemplate transferWithCategory =
                new PlannedTemplate(TransactionKind.TRANSFER, Money.brl("10.00"), wallet, wallet, mercado, null, null);

        assertThatThrownBy(() -> planned.check(actor, dollars))
                .isInstanceOfSatisfying(
                        DomainException.class, e -> assertThat(e.code()).isEqualTo("transaction.currency-mismatch"));
        assertThatThrownBy(() -> planned.check(actor, transferWithCategory))
                .isInstanceOfSatisfying(
                        DomainException.class, e -> assertThat(e.code()).isEqualTo("transaction.transfer-invalid"));
        planned.check(actor, template());
    }

    @Test
    void dropOpenFromKeepsConfirmedDeletedAndPast() {
        UUID rule = rule();
        LocalDate today = LocalDate.of(2026, 11, 23);
        UUID past = plannedRow(rule, today.minusDays(3));
        UUID openFuture = plannedRow(rule, today.plusDays(5));
        UUID confirmed = plannedRow(rule, today.plusDays(10));
        UUID skipped = plannedRow(rule, today.plusDays(15));
        jdbc.sql("update transactions set status = 'CONFIRMED' where id = :id")
                .param("id", confirmed)
                .update();
        jdbc.sql("update transactions set deleted_at = now() where id = :id")
                .param("id", skipped)
                .update();

        assertThat(planned.dropOpenFrom(ws, rule, today)).isOne();

        assertThat(exists(past)).isTrue();
        assertThat(exists(openFuture)).isFalse();
        assertThat(exists(confirmed)).isTrue();
        assertThat(exists(skipped)).isTrue();
    }

    private PlannedTemplate template() {
        return new PlannedTemplate(TransactionKind.EXPENSE, Money.brl("10.00"), wallet, null, mercado, null, "Aluguel");
    }

    private UUID plannedRow(UUID rule, LocalDate on) {
        return ledger.insertPlanned(ws, wallet, mercado, Money.brl("10.00"), on, rule, membership);
    }

    private boolean exists(UUID id) {
        return jdbc.sql("select count(*) from transactions where id = :id")
                        .param("id", id)
                        .query(Long.class)
                        .single()
                == 1;
    }

    private long count(String condition) {
        return jdbc.sql("select count(*) from transactions where workspace_id = :ws and " + condition)
                .param("ws", ws)
                .query(Long.class)
                .single();
    }

    private UUID rule() {
        return jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, category_id, frequency, interval,
                             start_on)
                        values (:ws, 'EXPENSE', 10, 'BRL', :account, :category, 'MONTHLY', 1, '2026-11-05')
                        returning id""")
                .param("ws", ws)
                .param("account", wallet)
                .param("category", mercado)
                .query(UUID.class)
                .single();
    }
}

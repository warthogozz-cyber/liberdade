package br.com.liberdadefamilia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestIds;
import br.com.liberdadefamilia.support.TestLedger;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The monthly income and expense of one venture: confirmed money marked with it, and only the family's own. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LedgerFiguresVentureIT {

    @Autowired
    LedgerFigures figures;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Autowired
    TestLedger ledger;

    @Test
    void sumsByMonth() {
        Family f = family();
        UUID venture = venture(f);
        mark(f, venture, "INCOME", "100.10", date(2026, 11, 3), "CONFIRMED", false);
        mark(f, venture, "INCOME", "50.25", date(2026, 11, 20), "CONFIRMED", false);
        mark(f, venture, "EXPENSE", "30.05", date(2026, 11, 10), "CONFIRMED", false);
        mark(f, venture, "EXPENSE", "12.50", date(2026, 12, 1), "CONFIRMED", false);

        List<VentureMonth> months = figures.ventureMonths(f.workspaceId(), venture);

        assertThat(months)
                .extracting(VentureMonth::month)
                .containsExactly(YearMonth.of(2026, 11), YearMonth.of(2026, 12));
        assertThat(months.get(0).income()).isEqualByComparingTo("150.35");
        assertThat(months.get(0).expense()).isEqualByComparingTo("30.05");
        assertThat(months.get(1).income()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(months.get(1).expense()).isEqualByComparingTo("12.50");
    }

    @Test
    void skipsPlannedAndDeleted() {
        Family f = family();
        UUID venture = venture(f);
        mark(f, venture, "EXPENSE", "10", date(2026, 11, 3), "CONFIRMED", false);
        mark(f, venture, "EXPENSE", "500", date(2026, 11, 4), "PLANNED", false);
        mark(f, venture, "INCOME", "700", date(2026, 11, 5), "CONFIRMED", true);

        List<VentureMonth> months = figures.ventureMonths(f.workspaceId(), venture);

        assertThat(months).hasSize(1);
        assertThat(months.get(0).expense()).isEqualByComparingTo("10");
        assertThat(months.get(0).income()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void skipsOtherVentures() {
        Family f = family();
        UUID venture = venture(f);
        UUID other = venture(f);
        mark(f, venture, "INCOME", "40", date(2026, 11, 3), "CONFIRMED", false);
        mark(f, other, "INCOME", "999", date(2026, 11, 3), "CONFIRMED", false);
        mark(f, null, "INCOME", "888", date(2026, 11, 3), "CONFIRMED", false);

        List<VentureMonth> months = figures.ventureMonths(f.workspaceId(), venture);

        assertThat(months).hasSize(1);
        assertThat(months.get(0).income()).isEqualByComparingTo("40");
    }

    @Test
    void monthsWithoutTransactionsAreAbsent() {
        Family f = family();
        UUID venture = venture(f);
        mark(f, venture, "EXPENSE", "10", date(2026, 10, 15), "CONFIRMED", false);
        mark(f, venture, "EXPENSE", "20", date(2026, 12, 15), "CONFIRMED", false);

        List<VentureMonth> months = figures.ventureMonths(f.workspaceId(), venture);

        assertThat(months)
                .extracting(VentureMonth::month)
                .containsExactly(YearMonth.of(2026, 10), YearMonth.of(2026, 12));
    }

    @Test
    void otherFamilyGetsNothing() {
        Family f = family();
        Family stranger = family();
        UUID venture = venture(f);
        mark(f, venture, "INCOME", "40", date(2026, 11, 3), "CONFIRMED", false);

        assertThat(figures.ventureMonths(stranger.workspaceId(), venture)).isEmpty();
    }

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private UUID venture(Family f) {
        return jdbc.sql("""
                        insert into ventures (workspace_id, name, type, status, started_on, currency)
                        values (:ws, 'Doces', 'FOOD', 'ACTIVE', date '2026-10-01', 'BRL')
                        returning id""").param("ws", f.workspaceId()).query(UUID.class).single();
    }

    private void mark(
            Family f,
            @Nullable UUID venture,
            String kind,
            String amount,
            LocalDate on,
            String status,
            boolean deleted) {
        UUID category = ledger.categoryOf(f.workspaceId(), kind, "INCOME".equals(kind) ? "Salário" : "Mercado");
        jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on, account_id, category_id,
                             member_id, created_by, status, venture_id, deleted_at)
                        values (:id, :ws, :kind, cast(:amount as numeric), 'BRL', :on, :account, :category,
                                :member, :member, :status, :venture, case when :deleted then now() end)""")
                .param("id", TestIds.v7())
                .param("ws", f.workspaceId())
                .param("kind", kind)
                .param("amount", amount)
                .param("on", on)
                .param("account", f.wallet())
                .param("category", category)
                .param("member", f.membershipId())
                .param("status", status)
                .param("venture", venture)
                .param("deleted", deleted)
                .update();
    }

    private Family family() {
        UUID userId = users.create("Ana Teste", "ana" + UUID.randomUUID() + "@example.com");
        UUID workspaceId = workspaces.createFor(userId);
        return new Family(workspaceId, workspaces.membershipOf(workspaceId, userId), ledger.walletOf(workspaceId));
    }

    private record Family(UUID workspaceId, UUID membershipId, UUID wallet) {}
}

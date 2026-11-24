package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.AccountRef;
import br.com.liberdadefamilia.ledger.CategoryRef;
import br.com.liberdadefamilia.ledger.CategorySpending;
import br.com.liberdadefamilia.ledger.LedgerFigures;
import br.com.liberdadefamilia.ledger.MonthTotal;
import br.com.liberdadefamilia.ledger.ScheduledFlow;
import br.com.liberdadefamilia.ledger.SpendingSplit;
import br.com.liberdadefamilia.ledger.TransactionKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link LedgerFigures} over the ledger's tables. Every query filters by the family, and every sum leaves planned and
 * deleted transactions out unless the method says otherwise.
 */
@Component
@Transactional(readOnly = true)
class JdbcLedgerFigures implements LedgerFigures {

    /** The balance of the accounts {@code a} the caller's conditions pick, counting transactions up to {@code :day}. */
    private static final String BALANCE_ON = """
            select coalesce(sum(a.initial_balance), 0)
                   + coalesce((select sum(%s)
                               from transactions t
                               join accounts a
                                 on a.workspace_id = t.workspace_id
                                and (a.id = t.account_id or a.id = t.to_account_id)
                               where t.workspace_id = :workspaceId
                                 and t.status = 'CONFIRMED'
                                 and t.deleted_at is null
                                 and t.occurred_on <= :day
                                 and a.archived_at is null
                                 and %%1$s), 0)
            from accounts a
            where a.workspace_id = :workspaceId
              and a.archived_at is null
              and %%1$s""".formatted(AccountRepository.MOVEMENT);

    /** Confirmed, not deleted spending of the family, with its category {@code c} and the parent {@code p}. */
    private static final String CONFIRMED_EXPENSES = """
            from transactions t
            join categories c on c.workspace_id = t.workspace_id and c.id = t.category_id
            left join categories p on p.workspace_id = c.workspace_id and p.id = c.parent_id
            where t.workspace_id = :workspaceId
              and t.kind = 'EXPENSE'
              and t.status = 'CONFIRMED'
              and t.deleted_at is null
            """;

    private final JdbcClient jdbc;

    JdbcLedgerFigures(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CategoryRef> category(UUID workspaceId, UUID categoryId) {
        return jdbc.sql("""
                        select id, parent_id, kind, archived_at is not null as archived
                        from categories
                        where workspace_id = :workspaceId
                          and id = :categoryId""")
                .param("workspaceId", workspaceId)
                .param("categoryId", categoryId)
                .query((rs, row) -> new CategoryRef(
                        rs.getObject("id", UUID.class),
                        rs.getObject("parent_id", UUID.class),
                        TransactionKind.valueOf(rs.getString("kind")),
                        rs.getBoolean("archived")))
                .optional();
    }

    @Override
    public List<CategorySpending> confirmedExpenseByCategory(UUID workspaceId, LocalDate from, LocalDate to) {
        return jdbc.sql("select c.id, c.parent_id, sum(t.amount) as amount\n" + CONFIRMED_EXPENSES + """
                          and t.occurred_on between :from and :to
                        group by c.id, c.parent_id
                        order by c.id""")
                .param("workspaceId", workspaceId)
                .param("from", from)
                .param("to", to)
                .query((rs, row) -> new CategorySpending(
                        rs.getObject("id", UUID.class),
                        rs.getObject("parent_id", UUID.class),
                        rs.getBigDecimal("amount")))
                .list();
    }

    @Override
    public Optional<AccountRef> account(UUID workspaceId, UUID accountId) {
        return jdbc.sql("""
                        select id, currency, archived_at is not null as archived
                        from accounts
                        where workspace_id = :workspaceId
                          and id = :accountId""")
                .param("workspaceId", workspaceId)
                .param("accountId", accountId)
                .query((rs, row) -> new AccountRef(
                        rs.getObject("id", UUID.class), rs.getString("currency"), rs.getBoolean("archived")))
                .optional();
    }

    @Override
    public BigDecimal accountBalance(UUID workspaceId, UUID accountId) {
        return jdbc.sql("""
                        select a.initial_balance + coalesce(sum(%s), 0)
                        from accounts a
                        left join transactions t
                               on t.workspace_id = a.workspace_id
                              and (t.account_id = a.id or t.to_account_id = a.id)
                              and t.deleted_at is null
                              and t.status = 'CONFIRMED'
                        where a.workspace_id = :workspaceId
                          and a.id = :accountId
                        group by a.id""".formatted(AccountRepository.MOVEMENT))
                .param("workspaceId", workspaceId)
                .param("accountId", accountId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal activeBalanceOn(UUID workspaceId, LocalDate day) {
        return balanceOn(workspaceId, day, "true");
    }

    @Override
    public BigDecimal reserveBalanceOn(UUID workspaceId, LocalDate day) {
        return balanceOn(workspaceId, day, "a.is_reserve");
    }

    @Override
    public boolean hasReserveAccounts(UUID workspaceId) {
        return jdbc.sql("""
                        select exists (
                            select 1 from accounts
                            where workspace_id = :workspaceId and is_reserve and archived_at is null)""")
                .param("workspaceId", workspaceId)
                .query(Boolean.class)
                .single();
    }

    private BigDecimal balanceOn(UUID workspaceId, LocalDate day, String accountCondition) {
        return jdbc.sql(BALANCE_ON.formatted(accountCondition))
                .param("workspaceId", workspaceId)
                .param("day", day)
                .query(BigDecimal.class)
                .single();
    }

    @Override
    public List<MonthTotal> confirmedExpenseByMonth(
            UUID workspaceId, YearMonth from, YearMonth to, boolean withoutRuleOnly) {
        return jdbc.sql("""
                        select date_trunc('month', t.occurred_on)::date as month, sum(t.amount) as amount
                        from transactions t
                        where t.workspace_id = :workspaceId
                          and t.kind = 'EXPENSE'
                          and t.status = 'CONFIRMED'
                          and t.deleted_at is null
                          and t.occurred_on between :from and :to
                          and (not :withoutRuleOnly or t.recurring_rule_id is null)
                        group by 1
                        order by 1""")
                .param("workspaceId", workspaceId)
                .param("from", from.atDay(1))
                .param("to", to.atEndOfMonth())
                .param("withoutRuleOnly", withoutRuleOnly)
                .query((rs, row) -> new MonthTotal(
                        YearMonth.from(rs.getObject("month", LocalDate.class)), rs.getBigDecimal("amount")))
                .list();
    }

    @Override
    public Optional<LocalDate> firstConfirmedOn(UUID workspaceId) {
        return jdbc.sql("""
                        select occurred_on
                        from transactions
                        where workspace_id = :workspaceId
                          and status = 'CONFIRMED'
                          and deleted_at is null
                        order by occurred_on
                        limit 1""")
                .param("workspaceId", workspaceId)
                .query((rs, row) -> rs.getObject("occurred_on", LocalDate.class))
                .optional();
    }

    @Override
    public SpendingSplit fixedAndVariable(UUID workspaceId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select coalesce(sum(t.amount) filter (where fixed), 0) as fixed,
                               coalesce(sum(t.amount) filter (where not fixed), 0) as variable
                        from (select t.amount,
                                     case when t.recurring_rule_id is not null or c.is_fixed or p.is_fixed
                                          then true else false end as fixed
                        """ + CONFIRMED_EXPENSES + """
                                and t.occurred_on between :from and :to) t""")
                .param("workspaceId", workspaceId)
                .param("from", from)
                .param("to", to)
                .query((rs, row) -> new SpendingSplit(rs.getBigDecimal("fixed"), rs.getBigDecimal("variable")))
                .single();
    }

    @Override
    public List<ScheduledFlow> openFlowsAfter(UUID workspaceId, LocalDate today, LocalDate until) {
        return jdbc.sql("""
                        select occurred_on, kind, amount
                        from transactions
                        where workspace_id = :workspaceId
                          and deleted_at is null
                          and occurred_on <= :until
                          and (status = 'PLANNED' or (status = 'CONFIRMED' and occurred_on > :today))
                        order by occurred_on, id""")
                .param("workspaceId", workspaceId)
                .param("today", today)
                .param("until", until)
                .query((rs, row) -> new ScheduledFlow(
                        rs.getObject("occurred_on", LocalDate.class),
                        TransactionKind.valueOf(rs.getString("kind")),
                        rs.getBigDecimal("amount")))
                .list();
    }

    @Override
    public Set<LocalDate> occupiedDates(UUID workspaceId, UUID ruleId) {
        return new HashSet<>(jdbc.sql("""
                        select scheduled_on
                        from transactions
                        where workspace_id = :workspaceId
                          and recurring_rule_id = :ruleId""")
                .param("workspaceId", workspaceId)
                .param("ruleId", ruleId)
                .query((rs, row) -> rs.getObject("scheduled_on", LocalDate.class))
                .list());
    }
}

package br.com.liberdadefamilia.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The ledger figures the planning module reads. Every method looks only at the given family. Sums count confirmed,
 * not deleted transactions, unless the method says otherwise. Periods include both ends.
 */
public interface LedgerFigures {

    /** The family's category, archived or not; empty when the family has no such category. */
    Optional<CategoryRef> category(UUID workspaceId, UUID categoryId);

    /** One row for each category with confirmed spending from {@code from} to {@code to}, with its parent. */
    List<CategorySpending> confirmedExpenseByCategory(UUID workspaceId, LocalDate from, LocalDate to);

    /** The family's account, archived or not; empty when the family has no such account. */
    Optional<AccountRef> account(UUID workspaceId, UUID accountId);

    /** The balance the accounts screen shows for the account; zero when the family has no such account. */
    BigDecimal accountBalance(UUID workspaceId, UUID accountId);

    /** The balance of the accounts that are not archived, counting transactions dated up to {@code day}. */
    BigDecimal activeBalanceOn(UUID workspaceId, LocalDate day);

    /** The balance of the reserve accounts that are not archived, counting transactions dated up to {@code day}. */
    BigDecimal reserveBalanceOn(UUID workspaceId, LocalDate day);

    /**
     * The confirmed spending of each month from {@code from} to {@code to} that has any, oldest first. Transfers never
     * count. With {@code withoutRuleOnly}, only the spending that came from no recurring rule.
     */
    List<MonthTotal> confirmedExpenseByMonth(UUID workspaceId, YearMonth from, YearMonth to, boolean withoutRuleOnly);

    /** The date of the family's first confirmed transaction of any kind; empty when it has none. */
    Optional<LocalDate> firstConfirmedOn(UUID workspaceId);

    /** The confirmed spending from {@code from} to {@code to}, split into fixed and variable. */
    SpendingSplit fixedAndVariable(UUID workspaceId, LocalDate from, LocalDate to);

    /**
     * The money still to come: the open planned transactions dated up to {@code until}, overdue ones included, and the
     * confirmed ones dated after {@code today} and up to {@code until}. By date.
     */
    List<ScheduledFlow> openFlowsAfter(UUID workspaceId, LocalDate today, LocalDate until);

    /** Every date the rule already has an occurrence for, skipped (deleted) ones included. */
    Set<LocalDate> occupiedDates(UUID workspaceId, UUID ruleId);
}

package br.com.liberdadefamilia.ledger.internal;

import br.com.liberdadefamilia.ledger.PlannedTemplate;
import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.ledger.domain.CategoryTotal;
import br.com.liberdadefamilia.ledger.domain.Flow;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The family's transactions. Deleted ones are left out of every list and sum; they stay findable by id until purged,
 * so that they can be restored.
 */
@Component
class TransactionRepository {

    /**
     * The columns of a {@link TransactionView}, tags included. Right after an insert or an update, the tags in {@code
     * returning} are those from before it: the service sets the saved ones with {@link TransactionView#withTags}.
     */
    private static final String COLUMNS = """
            id, kind, amount, currency, occurred_on, account_id, to_account_id, category_id, description, payment_method,
            status, scheduled_on, recurring_rule_id, member_id, created_at, version,
            (select coalesce(array_agg(tg.name order by lower(tg.name), tg.id), cast('{}' as text[]))
             from transaction_tags tt
             join tags tg on tg.workspace_id = tt.workspace_id and tg.id = tt.tag_id
             where tt.workspace_id = transactions.workspace_id and tt.transaction_id = transactions.id) as tags""";

    /** The transaction touches the scope: it leaves or enters an account in it (a transfer from outside included). */
    private static final String VISIBLE = AccountScope.sqlTouches("account_id", "to_account_id");

    /**
     * Under a restricted scope, the caller may change the transaction: they recorded it, and every account it touches
     * is in the scope. Under {@link AccountScope#all()}, always true.
     */
    private static final String OWN = """
            (cast(:scopeIds as uuid[]) is null
             or (created_by = :caller
                 and account_id = any(cast(:scopeIds as uuid[]))
                 and (to_account_id is null or to_account_id = any(cast(:scopeIds as uuid[])))))""";

    /**
     * Under a restricted scope, the caller holds the transaction: every account it touches is in the scope, whoever
     * recorded it. This is who may confirm or skip a planned transaction, which a rule made, not the youth. Under {@link
     * AccountScope#all()}, always true.
     */
    private static final String HELD = """
            (cast(:scopeIds as uuid[]) is null
             or (account_id = any(cast(:scopeIds as uuid[]))
                 and (to_account_id is null or to_account_id = any(cast(:scopeIds as uuid[])))))""";

    /**
     * Who may restore: the one who may change it ({@link #OWN}), or, for a row a recurring rule made, anyone who holds
     * it ({@link #HELD}), so that the youth can undo skipping a planned transaction the rule's author made.
     */
    private static final String RESTORABLE = "(" + OWN + " or (recurring_rule_id is not null and " + HELD + "))";

    private final JdbcClient jdbc;

    TransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts the transaction, recorded by one member and for another (or the same), unless its id already exists (in
     * this family or any other). Returns the new row, or empty when the id was taken.
     */
    Optional<TransactionView> insertIfAbsent(
            UUID workspaceId, UUID createdBy, UUID memberId, CreateTransactionRequest request) {
        return jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on,
                             account_id, to_account_id, category_id, description, payment_method, member_id,
                             created_by)
                        values (:id, :workspaceId, :kind, :amount, :currency, :occurredOn,
                                :accountId, :toAccountId, :categoryId, :description, :paymentMethod, :memberId,
                                :createdBy)
                        on conflict (id) do nothing
                        returning\s""" + COLUMNS)
                .param("id", request.id())
                .param("workspaceId", workspaceId)
                .param("kind", request.kind().name())
                .param("amount", request.amount().amount())
                .param("currency", request.amount().currency().getCurrencyCode())
                .param("occurredOn", request.occurredOn())
                .param("accountId", request.accountId())
                .param("toAccountId", request.toAccountId())
                .param("categoryId", request.categoryId())
                .param("description", request.description())
                .param("paymentMethod", methodName(request.paymentMethod()))
                .param("memberId", memberId)
                .param("createdBy", createdBy)
                .query(TransactionRepository::view)
                .optional();
    }

    /**
     * A transaction of this family that touches the scope, deleted or not: an id stays taken after deletion. One out
     * of the scope is as absent as one that never existed.
     */
    Optional<StoredTransaction> find(UUID workspaceId, UUID id, AccountScope scope) {
        return jdbc.sql("select " + COLUMNS + """
                        , created_by, deleted_at is not null as deleted
                         from transactions
                        where workspace_id = :workspaceId
                          and id = :id
                          and """ + VISIBLE)
                .param("workspaceId", workspaceId)
                .param("id", id)
                .param("scopeIds", scope.sqlArray())
                .query(TransactionRepository::stored)
                .optional();
    }

    /** Like {@link #findOwn}, for what the caller may restore (see {@link #RESTORABLE}). */
    Optional<StoredTransaction> findRestorable(UUID workspaceId, UUID id, AccountScope scope, UUID caller) {
        return jdbc.sql("select " + COLUMNS + """
                        , created_by, deleted_at is not null as deleted
                         from transactions
                        where workspace_id = :workspaceId
                          and id = :id
                          and """ + RESTORABLE)
                .param("workspaceId", workspaceId)
                .param("id", id)
                .param("scopeIds", scope.sqlArray())
                .param("caller", caller)
                .query(TransactionRepository::stored)
                .optional();
    }

    /**
     * A transaction of this family, deleted or not, that the caller may change under the scope: under a restricted
     * one, only what they recorded with every account in the scope.
     */
    Optional<StoredTransaction> findOwn(UUID workspaceId, UUID id, AccountScope scope, UUID caller) {
        return jdbc.sql("select " + COLUMNS + """
                        , created_by, deleted_at is not null as deleted
                         from transactions
                        where workspace_id = :workspaceId
                          and id = :id
                          and """ + OWN)
                .param("workspaceId", workspaceId)
                .param("id", id)
                .param("scopeIds", scope.sqlArray())
                .param("caller", caller)
                .query(TransactionRepository::stored)
                .optional();
    }

    /**
     * Replaces the transaction's data, if it is still at the given version, confirmed, not deleted and the caller's to
     * change under the scope, and moves it to the next version. Empty when the version moved on, the transaction was
     * deleted or turned planned (or never existed, or is not the caller's): a planned one changes only by confirming.
     */
    Optional<TransactionView> update(
            UUID workspaceId,
            UUID id,
            AccountScope scope,
            UUID caller,
            UUID memberId,
            UpdateTransactionRequest request) {
        return jdbc.sql("""
                        update transactions
                        set kind = :kind,
                            amount = :amount,
                            currency = :currency,
                            occurred_on = :occurredOn,
                            account_id = :accountId,
                            to_account_id = :toAccountId,
                            category_id = :categoryId,
                            description = :description,
                            payment_method = :paymentMethod,
                            member_id = :memberId,
                            version = version + 1,
                            updated_at = now()
                        where workspace_id = :workspaceId
                          and id = :id
                          and version = :version
                          and status = 'CONFIRMED'
                          and deleted_at is null
                          and """ + OWN + """
                        returning\s""" + COLUMNS)
                .param("workspaceId", workspaceId)
                .param("id", id)
                .param("scopeIds", scope.sqlArray())
                .param("caller", caller)
                .param("version", request.version())
                .param("memberId", memberId)
                .param("kind", request.kind().name())
                .param("amount", request.amount().amount())
                .param("currency", request.amount().currency().getCurrencyCode())
                .param("occurredOn", request.occurredOn())
                .param("accountId", request.accountId())
                .param("toAccountId", request.toAccountId())
                .param("categoryId", request.categoryId())
                .param("description", request.description())
                .param("paymentMethod", methodName(request.paymentMethod()))
                .query(TransactionRepository::view)
                .optional();
    }

    /**
     * Marks an active transaction that the caller may change under the scope as deleted at {@code now}. Whether one
     * was; false when absent, already deleted or not the caller's. The OWN condition here is the delete's only guard,
     * not a repetition of a check made elsewhere.
     */
    boolean softDelete(UUID workspaceId, UUID id, AccountScope scope, UUID caller, Instant now) {
        return jdbc.sql("""
                        update transactions
                        set deleted_at = :now,
                            version = version + 1,
                            updated_at = now()
                        where workspace_id = :workspaceId
                          and id = :id
                          and deleted_at is null
                          and """ + OWN)
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .param("scopeIds", scope.sqlArray())
                        .param("caller", caller)
                        .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                        .update()
                == 1;
    }

    /**
     * Brings back a deleted transaction that the caller may change under the scope (or, for a rule's, holds). Empty when
     * it is absent, not deleted or not the caller's. The RESTORABLE condition here is the restore's primary guard, not
     * a repetition of a check made elsewhere.
     */
    Optional<TransactionView> restore(UUID workspaceId, UUID id, AccountScope scope, UUID caller) {
        return jdbc.sql("""
                        update transactions
                        set deleted_at = null,
                            version = version + 1,
                            updated_at = now()
                        where workspace_id = :workspaceId
                          and id = :id
                          and deleted_at is not null
                          and """ + RESTORABLE + """
                        returning\s""" + COLUMNS)
                .param("workspaceId", workspaceId)
                .param("id", id)
                .param("scopeIds", scope.sqlArray())
                .param("caller", caller)
                .query(TransactionRepository::view)
                .optional();
    }

    /**
     * A transaction of this family, deleted or not, that the caller holds under the scope (see {@link #HELD}): the one
     * a confirm or a skip acts on. Out of the scope it is as absent as one that never existed.
     */
    Optional<StoredTransaction> findHeld(UUID workspaceId, UUID id, AccountScope scope) {
        return jdbc.sql("select " + COLUMNS + """
                        , created_by, deleted_at is not null as deleted
                         from transactions
                        where workspace_id = :workspaceId
                          and id = :id
                          and """ + HELD)
                .param("workspaceId", workspaceId)
                .param("id", id)
                .param("scopeIds", scope.sqlArray())
                .query(TransactionRepository::stored)
                .optional();
    }

    /**
     * Makes a planned transaction a confirmed one, with the data given, entered by {@code memberId}, if it is still at
     * the version, planned, not deleted and held by the caller under the scope. {@code created_by} and {@code
     * scheduled_on} stay. Empty when any of that stopped being true.
     */
    Optional<TransactionView> confirm(
            UUID workspaceId,
            UUID id,
            AccountScope scope,
            UUID memberId,
            long version,
            Money amount,
            LocalDate occurredOn,
            UUID accountId) {
        return jdbc.sql("""
                        update transactions
                        set status = 'CONFIRMED',
                            member_id = :memberId,
                            amount = :amount,
                            currency = :currency,
                            occurred_on = :occurredOn,
                            account_id = :accountId,
                            version = version + 1,
                            updated_at = now()
                        where workspace_id = :workspaceId
                          and id = :id
                          and deleted_at is null
                          and status = 'PLANNED'
                          and version = :version
                          and """ + HELD + """
                        returning\s""" + COLUMNS)
                .param("workspaceId", workspaceId)
                .param("id", id)
                .param("scopeIds", scope.sqlArray())
                .param("version", version)
                .param("memberId", memberId)
                .param("amount", amount.amount())
                .param("currency", amount.currency().getCurrencyCode())
                .param("occurredOn", occurredOn)
                .param("accountId", accountId)
                .query(TransactionRepository::view)
                .optional();
    }

    /**
     * Marks a planned transaction as deleted at {@code now}, if it is still at the version, planned, not deleted and
     * held by the caller under the scope. Whether it was; the restore of a deleted transaction brings it back.
     */
    boolean skip(UUID workspaceId, UUID id, AccountScope scope, long version, Instant now) {
        return jdbc.sql("""
                        update transactions
                        set deleted_at = :now,
                            version = version + 1,
                            updated_at = now()
                        where workspace_id = :workspaceId
                          and id = :id
                          and deleted_at is null
                          and status = 'PLANNED'
                          and version = :version
                          and """ + HELD)
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .param("scopeIds", scope.sqlArray())
                        .param("version", version)
                        .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                        .update()
                == 1;
    }

    /**
     * Plans one occurrence of a recurring rule, dated and scheduled on {@code scheduledOn}, made and entered by the
     * membership. Whether it was; false when the rule already has the occurrence (even deleted).
     */
    boolean insertPlanned(
            UUID workspaceId, UUID ruleId, LocalDate scheduledOn, PlannedTemplate template, UUID authorMembershipId) {
        return jdbc.sql("""
                        insert into transactions
                            (id, workspace_id, kind, amount, currency, occurred_on,
                             account_id, to_account_id, category_id, description, payment_method, member_id,
                             created_by, status, recurring_rule_id, scheduled_on)
                        values (uuidv7(), :workspaceId, :kind, :amount, :currency, :scheduledOn,
                                :accountId, :toAccountId, :categoryId, :description, :paymentMethod, :author,
                                :author, 'PLANNED', :ruleId, :scheduledOn)
                        on conflict do nothing""")
                        .param("workspaceId", workspaceId)
                        .param("kind", template.kind().name())
                        .param("amount", template.amount().amount())
                        .param("currency", template.amount().currency().getCurrencyCode())
                        .param("scheduledOn", scheduledOn)
                        .param("accountId", template.accountId())
                        .param("toAccountId", template.toAccountId())
                        .param("categoryId", template.categoryId())
                        .param("description", template.description())
                        .param("paymentMethod", methodName(template.paymentMethod()))
                        .param("author", authorMembershipId)
                        .param("ruleId", ruleId)
                        .update()
                == 1;
    }

    /**
     * Removes for good the rule's planned transactions that are not deleted and are scheduled on or after {@code from},
     * and returns how many. Confirmed and skipped ones stay, which keeps their occurrences taken.
     */
    int deletePlannedFrom(UUID workspaceId, UUID ruleId, LocalDate from) {
        return jdbc.sql("""
                        delete from transactions
                        where workspace_id = :workspaceId
                          and recurring_rule_id = :ruleId
                          and status = 'PLANNED'
                          and deleted_at is null
                          and scheduled_on >= :from""")
                .param("workspaceId", workspaceId)
                .param("ruleId", ruleId)
                .param("from", from)
                .update();
    }

    /**
     * The transactions on or after {@code from} and before {@code until}, newest first (D20). With a category, only
     * that category and its subcategories; with an account, the transactions leaving or entering it; with a payment
     * method, only those paid that way; with a tag, only those that have it. Only those that touch the scope. Planned
     * ones come only as {@code planned} says.
     */
    List<TransactionView> between(
            UUID workspaceId,
            LocalDate from,
            LocalDate until,
            @Nullable UUID categoryId,
            @Nullable UUID accountId,
            @Nullable PaymentMethod paymentMethod,
            @Nullable UUID tagId,
            Planned planned,
            AccountScope scope) {
        return jdbc.sql("select " + COLUMNS + """
                         from transactions
                        where workspace_id = :workspaceId
                          and (occurred_on >= :from and occurred_on < :until
                               or (cast(:overdue as boolean) and status = 'PLANNED' and occurred_on < :from))
                          and (status = 'CONFIRMED' or cast(:planning as boolean))
                          and deleted_at is null
                          and (cast(:categoryId as uuid) is null
                               or category_id in (select c.id
                                                  from categories c
                                                  where c.workspace_id = :workspaceId
                                                    and (c.id = cast(:categoryId as uuid)
                                                         or c.parent_id = cast(:categoryId as uuid))))
                          and (cast(:accountId as uuid) is null
                               or account_id = cast(:accountId as uuid)
                               or to_account_id = cast(:accountId as uuid))
                          and (cast(:paymentMethod as text) is null or payment_method = cast(:paymentMethod as text))
                          and (cast(:tagId as uuid) is null
                               or exists (select 1
                                          from transaction_tags tt
                                          where tt.workspace_id = transactions.workspace_id
                                            and tt.transaction_id = transactions.id
                                            and tt.tag_id = cast(:tagId as uuid)))
                          and """ + VISIBLE + """
                        order by occurred_on desc, created_at desc, id desc""")
                .param("workspaceId", workspaceId)
                .param("from", from)
                .param("until", until)
                .param("categoryId", categoryId)
                .param("accountId", accountId)
                .param("paymentMethod", methodName(paymentMethod))
                .param("tagId", tagId)
                .param("planning", planned.shown())
                .param("overdue", planned.overdue())
                .param("scopeIds", scope.sqlArray())
                .query(TransactionRepository::view)
                .list();
    }

    /**
     * What moved up to {@code monthEnd}, summed by day, kind, accounts and currency. What happened before the month
     * collapses into the day before {@code monthStart}, so that years of history come back as a few flows. Only what
     * touches the scope: a transfer from an account outside it into one inside counts in the balance alone.
     */
    List<Flow> flowsUntil(
            UUID workspaceId,
            LocalDate monthStart,
            LocalDate monthEnd,
            @Nullable PaymentMethod paymentMethod,
            AccountScope scope) {
        return jdbc.sql("""
                        select greatest(occurred_on, cast(:monthStart as date) - 1) as day,
                               kind, account_id, to_account_id, currency, sum(amount) as amount
                        from transactions
                        where workspace_id = :workspaceId
                          and occurred_on <= :monthEnd
                          and status = 'CONFIRMED'
                          and deleted_at is null
                          and (cast(:paymentMethod as text) is null or payment_method = cast(:paymentMethod as text))
                          and """ + VISIBLE + """
                        group by 1, kind, account_id, to_account_id, currency""")
                .param("workspaceId", workspaceId)
                .param("scopeIds", scope.sqlArray())
                .param("monthStart", monthStart)
                .param("monthEnd", monthEnd)
                .param("paymentMethod", methodName(paymentMethod))
                .query((rs, row) -> new Flow(
                        rs.getObject("day", LocalDate.class),
                        TransactionKind.valueOf(rs.getString("kind")),
                        rs.getObject("account_id", UUID.class),
                        rs.getObject("to_account_id", UUID.class),
                        Money.of(rs.getBigDecimal("amount"), Currency.getInstance(rs.getString("currency")))))
                .list();
    }

    /**
     * The expenses of the month and of the month before, by top-level category: a subcategory counts for its parent
     * (D9). One line for each category and currency. Only the expenses from accounts in the scope.
     */
    List<CategoryTotal> expensesByTopCategory(
            UUID workspaceId, YearMonth month, @Nullable PaymentMethod paymentMethod, AccountScope scope) {
        return jdbc.sql("""
                        select coalesce(c.parent_id, c.id) as category_id, t.currency,
                               coalesce(sum(t.amount) filter (where t.occurred_on >= :monthStart), 0) as current,
                               coalesce(sum(t.amount) filter (where t.occurred_on < :monthStart), 0) as previous
                        from transactions t
                        join categories c
                          on c.workspace_id = t.workspace_id
                         and c.id = t.category_id
                        where t.workspace_id = :workspaceId
                          and t.kind = 'EXPENSE'
                          and t.status = 'CONFIRMED'
                          and t.deleted_at is null
                          and t.occurred_on >= :previousStart
                          and t.occurred_on < :nextStart
                          and (cast(:paymentMethod as text) is null or t.payment_method = cast(:paymentMethod as text))
                          and """ + AccountScope.sqlIncludes("t.account_id") + """
                        group by coalesce(c.parent_id, c.id), t.currency""")
                .param("workspaceId", workspaceId)
                .param("scopeIds", scope.sqlArray())
                .param("monthStart", month.atDay(1))
                .param("previousStart", month.minusMonths(1).atDay(1))
                .param("nextStart", month.plusMonths(1).atDay(1))
                .param("paymentMethod", methodName(paymentMethod))
                .query((rs, row) -> {
                    Currency currency = Currency.getInstance(rs.getString("currency"));
                    return new CategoryTotal(
                            rs.getObject("category_id", UUID.class),
                            Money.of(rs.getBigDecimal("current"), currency),
                            Money.of(rs.getBigDecimal("previous"), currency));
                })
                .list();
    }

    /**
     * Removes for good the transactions deleted before the cutoff, in every family, and returns how many. The one method
     * here without a {@code workspaceId}: the nightly purge sweeps all families at once and serves no request, so there
     * is no family to scope it to, and it only removes rows that their own families already deleted. A transaction of a
     * recurring rule stays, skipped or not: purging it would free its occurrence, and the generator would make it again.
     */
    int purgeDeletedBefore(Instant cutoff) {
        return jdbc.sql("""
                        delete from transactions
                        where deleted_at is not null
                          and recurring_rule_id is null
                          and deleted_at < :cutoff""")
                .param("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .update();
    }

    private static TransactionView view(ResultSet rs, int row) throws SQLException {
        return new TransactionView(
                rs.getObject("id", UUID.class),
                TransactionKind.valueOf(rs.getString("kind")),
                Money.of(rs.getBigDecimal("amount"), Currency.getInstance(rs.getString("currency"))),
                rs.getObject("occurred_on", LocalDate.class),
                rs.getObject("account_id", UUID.class),
                rs.getObject("to_account_id", UUID.class),
                rs.getObject("category_id", UUID.class),
                rs.getString("description"),
                method(rs.getString("payment_method")),
                tagNames(rs.getArray("tags")),
                rs.getString("status"),
                rs.getObject("scheduled_on", LocalDate.class),
                rs.getObject("recurring_rule_id", UUID.class),
                rs.getObject("member_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getLong("version"));
    }

    private static List<String> tagNames(Array array) throws SQLException {
        return List.of((String[]) array.getArray());
    }

    private static @Nullable String methodName(@Nullable PaymentMethod method) {
        return method == null ? null : method.name();
    }

    private static @Nullable PaymentMethod method(@Nullable String name) {
        return name == null ? null : PaymentMethod.valueOf(name);
    }

    private static StoredTransaction stored(ResultSet rs, int row) throws SQLException {
        return new StoredTransaction(view(rs, row), rs.getObject("created_by", UUID.class), rs.getBoolean("deleted"));
    }

    /**
     * A transaction as stored, with the membership that recorded it and whether it is deleted (and so restorable, not
     * editable).
     */
    record StoredTransaction(TransactionView view, UUID createdBy, boolean deleted) {}

    /**
     * Whether a list brings the planned transactions: {@code shown} when the planning module is on, and {@code overdue}
     * to also bring those of earlier months, which are still waiting.
     */
    record Planned(boolean shown, boolean overdue) {}
}

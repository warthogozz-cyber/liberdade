package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.planning.domain.Frequency;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The family's recurring rules. Every query is for one family. */
@Component
class RecurringRuleRepository {

    private static final String COLUMNS = """
            id, kind, amount, currency, account_id, to_account_id, category_id, payment_method, description,
            frequency, "interval", start_on, end_on, next_due_on, created_by, version""";

    private final JdbcClient jdbc;

    RecurringRuleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The active rules the member may see. A youth sees the rules that leave or enter an account they own, archived
     * accounts included: the same rule of scope the ledger applies to transactions, kept here in SQL because the
     * ledger keeps its own out of reach.
     */
    List<RecurringRuleView> listActive(UUID workspaceId, UUID membershipId, boolean restrictedToOwnAccounts) {
        return jdbc.sql("select " + COLUMNS + """

                        from recurring_rules r
                        where r.workspace_id = :workspaceId
                          and r.archived_at is null
                          and (not :restricted
                               or r.account_id in (select a.id from accounts a
                                                   where a.workspace_id = r.workspace_id
                                                     and a.owner_membership_id = :membershipId)
                               or r.to_account_id in (select a.id from accounts a
                                                      where a.workspace_id = r.workspace_id
                                                        and a.owner_membership_id = :membershipId))
                        order by r.start_on, r.id""")
                .param("workspaceId", workspaceId)
                .param("membershipId", membershipId)
                .param("restricted", restrictedToOwnAccounts)
                .query(RecurringRuleRepository::view)
                .list();
    }

    Optional<RecurringRuleView> findActive(UUID workspaceId, UUID id) {
        return jdbc.sql("select " + COLUMNS + """

                        from recurring_rules
                        where workspace_id = :workspaceId and id = :id and archived_at is null""")
                .param("workspaceId", workspaceId)
                .param("id", id)
                .query(RecurringRuleRepository::view)
                .optional();
    }

    /**
     * The active rule, locked until the transaction ends, so a rule archived at the same time cannot get planned rows
     * after its archiving.
     */
    Optional<RecurringRuleView> lockActive(UUID workspaceId, UUID id) {
        return jdbc.sql("select " + COLUMNS + """

                        from recurring_rules
                        where workspace_id = :workspaceId and id = :id and archived_at is null
                        for update""")
                .param("workspaceId", workspaceId)
                .param("id", id)
                .query(RecurringRuleRepository::view)
                .optional();
    }

    /** The families that have an active rule with occurrences still to plan. */
    List<UUID> familiesWithRulesToPlan() {
        return jdbc.sql("""
                        select distinct workspace_id from recurring_rules
                        where archived_at is null and next_due_on is not null
                        order by workspace_id""").query(UUID.class).list();
    }

    /** The ids of the family's active rules. */
    List<UUID> activeIds(UUID workspaceId) {
        return jdbc.sql("""
                        select id from recurring_rules
                        where workspace_id = :workspaceId and archived_at is null
                        order by start_on, id""").param("workspaceId", workspaceId).query(UUID.class).list();
    }

    /** Whether the family has the rule, archived or not. */
    boolean exists(UUID workspaceId, UUID id) {
        return jdbc.sql("select count(*) from recurring_rules where workspace_id = :workspaceId and id = :id")
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .query(Integer.class)
                        .single()
                > 0;
    }

    UUID insert(UUID workspaceId, CreateRecurringRuleRequest request, UUID createdBy) {
        return jdbc.sql("""
                        insert into recurring_rules
                            (workspace_id, kind, amount, currency, account_id, to_account_id, category_id,
                             payment_method, description, frequency, "interval", start_on, end_on, next_due_on,
                             created_by)
                        values (:workspaceId, :kind, :amount, :currency, :accountId, :toAccountId, :categoryId,
                                :paymentMethod, :description, :frequency, :interval, :startOn, :endOn, :startOn,
                                :createdBy)
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("kind", request.kind().name())
                .param("amount", request.amount().amount())
                .param("currency", request.amount().currency().getCurrencyCode())
                .param("accountId", request.accountId())
                .param("toAccountId", request.toAccountId())
                .param("categoryId", request.categoryId())
                .param("paymentMethod", methodName(request.paymentMethod()))
                .param("description", request.description())
                .param("frequency", request.frequency().name())
                .param("interval", request.interval())
                .param("startOn", request.startOn())
                .param("endOn", request.endOn())
                .param("createdBy", createdBy)
                .query(UUID.class)
                .single();
    }

    /** Replaces the rule if the version is still the caller's; the next date goes back to the start. */
    boolean update(UUID workspaceId, UUID id, UpdateRecurringRuleRequest request) {
        return jdbc.sql("""
                        update recurring_rules
                        set kind = :kind, amount = :amount, currency = :currency, account_id = :accountId,
                            to_account_id = :toAccountId, category_id = :categoryId,
                            payment_method = :paymentMethod, description = :description, frequency = :frequency,
                            "interval" = :interval, start_on = :startOn, end_on = :endOn, next_due_on = :startOn,
                            updated_at = now(), version = version + 1
                        where workspace_id = :workspaceId and id = :id and archived_at is null
                          and version = :version""")
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .param("version", request.version())
                        .param("kind", request.kind().name())
                        .param("amount", request.amount().amount())
                        .param("currency", request.amount().currency().getCurrencyCode())
                        .param("accountId", request.accountId())
                        .param("toAccountId", request.toAccountId())
                        .param("categoryId", request.categoryId())
                        .param("paymentMethod", methodName(request.paymentMethod()))
                        .param("description", request.description())
                        .param("frequency", request.frequency().name())
                        .param("interval", request.interval())
                        .param("startOn", request.startOn())
                        .param("endOn", request.endOn())
                        .update()
                > 0;
    }

    /** @return false when the rule is not active, archived already included */
    boolean archive(UUID workspaceId, UUID id, Instant at) {
        return jdbc.sql("""
                        update recurring_rules
                        set archived_at = :at, updated_at = :at, version = version + 1
                        where workspace_id = :workspaceId and id = :id and archived_at is null""")
                        .param("workspaceId", workspaceId)
                        .param("id", id)
                        .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC))
                        .update()
                > 0;
    }

    void setNextDueOn(UUID workspaceId, UUID id, @Nullable LocalDate nextDueOn) {
        jdbc.sql("""
                        update recurring_rules set next_due_on = :nextDueOn
                        where workspace_id = :workspaceId and id = :id""")
                .param("workspaceId", workspaceId)
                .param("id", id)
                .param("nextDueOn", nextDueOn)
                .update();
    }

    private static RecurringRuleView view(ResultSet rs, int row) throws SQLException {
        Currency currency = Currency.getInstance(rs.getString("currency").strip());
        String method = rs.getString("payment_method");
        return new RecurringRuleView(
                rs.getObject("id", UUID.class),
                TransactionKind.valueOf(rs.getString("kind")),
                Money.of(rs.getBigDecimal("amount"), currency),
                rs.getObject("account_id", UUID.class),
                rs.getObject("to_account_id", UUID.class),
                rs.getObject("category_id", UUID.class),
                method == null ? null : PaymentMethod.valueOf(method),
                rs.getString("description"),
                Frequency.valueOf(rs.getString("frequency")),
                rs.getInt("interval"),
                rs.getObject("start_on", LocalDate.class),
                rs.getObject("end_on", LocalDate.class),
                rs.getObject("next_due_on", LocalDate.class),
                rs.getObject("created_by", UUID.class),
                rs.getLong("version"));
    }

    private static @Nullable String methodName(@Nullable PaymentMethod method) {
        return method == null ? null : method.name();
    }
}

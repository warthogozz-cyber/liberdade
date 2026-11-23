package br.com.liberdadefamilia.planning.internal;

import br.com.liberdadefamilia.ledger.PlannedTemplate;
import br.com.liberdadefamilia.ledger.PlannedTransactions;
import br.com.liberdadefamilia.planning.domain.RecurrenceSchedule;
import br.com.liberdadefamilia.workspace.Memberships;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plans the occurrences of a recurring rule from the first day of the current month to the last day of the next one
 * (D11). It plans as the rule's author, or as the family's oldest active owner when the author is gone (D12), and it
 * can run again at any time: an occurrence that exists, even a skipped or confirmed one, is never made twice.
 */
@Component
public class RecurrenceGenerator {

    private final RecurringRuleRepository rules;
    private final PlannedTransactions planned;
    private final Memberships memberships;
    private final Clock clock;

    RecurrenceGenerator(
            RecurringRuleRepository rules, PlannedTransactions planned, Memberships memberships, Clock clock) {
        this.rules = rules;
        this.planned = planned;
        this.memberships = memberships;
        this.clock = clock;
    }

    /** Does nothing for a rule that is archived, unknown to the family, or without a member to plan as. */
    @Transactional
    public void generate(UUID workspaceId, UUID ruleId) {
        Optional<RecurringRuleView> found = rules.lockActive(workspaceId, ruleId);
        if (found.isEmpty()) {
            return;
        }
        RecurringRuleView rule = found.get();
        Optional<WorkspaceMember> author = author(workspaceId, rule);
        LocalDate nextDueOn = rule.nextDueOn();
        if (author.isEmpty() || nextDueOn == null) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(author.get().timezone()));
        LocalDate horizonEnd = today.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
        LocalDate from = latest(latest(nextDueOn, rule.startOn()), today.withDayOfMonth(1));

        PlannedTemplate template = new PlannedTemplate(
                rule.kind(),
                rule.amount(),
                rule.accountId(),
                rule.toAccountId(),
                rule.categoryId(),
                rule.paymentMethod(),
                rule.description());
        List<LocalDate> dates = RecurrenceSchedule.occurrences(
                rule.frequency(), rule.interval(), rule.startOn(), rule.endOn(), from, horizonEnd);
        for (LocalDate date : dates) {
            planned.plan(workspaceId, ruleId, date, template, author.get().membershipId());
        }
        rules.setNextDueOn(
                workspaceId,
                ruleId,
                RecurrenceSchedule.firstAfter(
                                rule.frequency(), rule.interval(), rule.startOn(), rule.endOn(), horizonEnd)
                        .orElse(null));
    }

    /** Today where the rule is planned: in the zone of its author, the same the generator uses. */
    LocalDate today(UUID workspaceId, RecurringRuleView rule) {
        return author(workspaceId, rule)
                .map(author -> LocalDate.now(clock.withZone(author.timezone())))
                .orElseGet(() -> LocalDate.now(clock));
    }

    private Optional<WorkspaceMember> author(UUID workspaceId, RecurringRuleView rule) {
        Optional<WorkspaceMember> creator =
                rule.createdBy() == null ? Optional.empty() : memberships.activeMember(workspaceId, rule.createdBy());
        return creator.isPresent() ? creator : memberships.activeOwner(workspaceId);
    }

    private static LocalDate latest(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}

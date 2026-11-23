package br.com.liberdadefamilia.planning.internal;

import static br.com.liberdadefamilia.workspace.Role.YOUTH;

import br.com.liberdadefamilia.ledger.PlannedTemplate;
import br.com.liberdadefamilia.ledger.PlannedTransactions;
import br.com.liberdadefamilia.ledger.TransactionKind;
import br.com.liberdadefamilia.planning.RecurringRuleArchived;
import br.com.liberdadefamilia.planning.RecurringRuleCreated;
import br.com.liberdadefamilia.planning.RecurringRuleEdited;
import br.com.liberdadefamilia.planning.domain.RecurrenceSchedule;
import br.com.liberdadefamilia.shared.Money;
import br.com.liberdadefamilia.shared.PaymentMethod;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The family's recurring rules (D9, D11). Each write is one transaction: the rule, the planned transactions it makes or
 * drops, and the event.
 */
@Service
class RecurringRuleService {

    private final RecurringRuleRepository rules;
    private final RecurrenceGenerator generator;
    private final PlannedTransactions planned;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RecurringRuleService(
            RecurringRuleRepository rules,
            RecurrenceGenerator generator,
            PlannedTransactions planned,
            ApplicationEventPublisher events,
            Clock clock) {
        this.rules = rules;
        this.generator = generator;
        this.planned = planned;
        this.events = events;
        this.clock = clock;
    }

    /** The description without the blanks around it; nothing left means none. */
    static @Nullable String cleanDescription(@Nullable String description) {
        if (description == null) {
            return null;
        }
        String stripped = description.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    List<RecurringRuleView> list(WorkspaceMember member) {
        return rules.listActive(member.workspaceId(), member.membershipId(), member.role() == YOUTH);
    }

    /**
     * @throws RecurringRuleRejectedException when the end is before the start
     */
    @Transactional
    RecurringRuleView create(WorkspaceMember member, CreateRecurringRuleRequest request) {
        requireEndNotBeforeStart(request.startOn(), request.endOn());
        planned.check(
                member,
                template(
                        request.kind(),
                        request.amount(),
                        request.accountId(),
                        request.toAccountId(),
                        request.categoryId(),
                        request.paymentMethod(),
                        request.description()));
        UUID workspaceId = member.workspaceId();
        UUID id = rules.insert(workspaceId, request, member.membershipId());
        generator.generate(workspaceId, id);
        events.publishEvent(new RecurringRuleCreated(workspaceId, id, member.userId()));
        return rules.findActive(workspaceId, id).orElseThrow(RecurringRuleNotFoundException::new);
    }

    /**
     * Replaces the rule and its open planned transactions from today on; what was confirmed, skipped or is overdue
     * stays as it is. The next date to plan is the first one of the edited schedule on or after today (in the zone the
     * generator plans in), so moving the start earlier in the current month makes no second occurrence.
     *
     * @throws RecurringRuleNotFoundException when the family has no such active rule
     * @throws RecurringRuleConflictException when the version is not the rule's
     */
    @Transactional
    RecurringRuleView update(WorkspaceMember member, UUID id, UpdateRecurringRuleRequest request) {
        requireEndNotBeforeStart(request.startOn(), request.endOn());
        UUID workspaceId = member.workspaceId();
        planned.check(
                member,
                template(
                        request.kind(),
                        request.amount(),
                        request.accountId(),
                        request.toAccountId(),
                        request.categoryId(),
                        request.paymentMethod(),
                        request.description()));
        RecurringRuleView current = rules.findActive(workspaceId, id).orElseThrow(RecurringRuleNotFoundException::new);
        LocalDate nextDueOn = RecurrenceSchedule.firstAfter(
                        request.frequency(),
                        request.interval(),
                        request.startOn(),
                        request.endOn(),
                        generator.today(workspaceId, current).minusDays(1))
                .orElse(null);
        if (!rules.update(workspaceId, id, request, nextDueOn)) {
            throw rules.findActive(workspaceId, id).isEmpty()
                    ? new RecurringRuleNotFoundException()
                    : new RecurringRuleConflictException();
        }
        planned.dropOpenFrom(workspaceId, id, today(member));
        generator.generate(workspaceId, id);
        events.publishEvent(new RecurringRuleEdited(workspaceId, id, member.userId()));
        return rules.findActive(workspaceId, id).orElseThrow(RecurringRuleNotFoundException::new);
    }

    /**
     * Archives the rule and drops its open planned transactions from today on. Archiving an archived rule changes
     * nothing and publishes nothing.
     *
     * @throws RecurringRuleNotFoundException when the family has no such rule
     */
    @Transactional
    void archive(WorkspaceMember member, UUID id) {
        UUID workspaceId = member.workspaceId();
        if (!rules.archive(workspaceId, id, clock.instant())) {
            if (!rules.exists(workspaceId, id)) {
                throw new RecurringRuleNotFoundException();
            }
            return;
        }
        planned.dropOpenFrom(workspaceId, id, today(member));
        events.publishEvent(new RecurringRuleArchived(workspaceId, id, member.userId()));
    }

    private LocalDate today(WorkspaceMember member) {
        return LocalDate.now(clock.withZone(member.timezone()));
    }

    private static void requireEndNotBeforeStart(LocalDate startOn, @Nullable LocalDate endOn) {
        if (endOn != null && endOn.isBefore(startOn)) {
            throw new RecurringRuleRejectedException(
                    "recurring-rule.end-before-start", "A data final não pode ser anterior à data inicial.");
        }
    }

    private static PlannedTemplate template(
            TransactionKind kind,
            Money amount,
            UUID accountId,
            @Nullable UUID toAccountId,
            @Nullable UUID categoryId,
            @Nullable PaymentMethod paymentMethod,
            @Nullable String description) {
        return new PlannedTemplate(kind, amount, accountId, toAccountId, categoryId, paymentMethod, description);
    }
}

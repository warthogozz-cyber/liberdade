package br.com.liberdadefamilia.ledger;

import br.com.liberdadefamilia.workspace.WorkspaceMember;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What the planning module may do with the family's transactions: check a mold, plan one occurrence of it, and drop the
 * planned ones that are still open. A planned transaction counts in no total until someone confirms it.
 */
public interface PlannedTransactions {

    /**
     * Applies the rules of a new transaction to the mold, for the member, and throws the same exceptions with the same
     * codes. The date is not part of the mold.
     */
    void check(WorkspaceMember actor, PlannedTemplate template);

    /**
     * Plans the occurrence of the rule due on {@code scheduledOn}, with that date as the day it falls on, made and
     * entered by the membership. A caller that skipped {@link #check} may get an exception for what it would reject.
     *
     * @return false when the occurrence already exists, even if it was skipped or confirmed since, or when its account
     *     or category is archived; true when it was planned
     */
    boolean plan(
            UUID workspaceId, UUID ruleId, LocalDate scheduledOn, PlannedTemplate template, UUID authorMembershipId);

    /**
     * Deletes for good the rule's planned transactions that nobody confirmed or skipped and that are due on or after
     * {@code from}.
     *
     * @return how many it deleted
     */
    int dropOpenFrom(UUID workspaceId, UUID ruleId, LocalDate from);
}

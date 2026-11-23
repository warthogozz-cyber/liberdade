package br.com.liberdadefamilia.planning;

import java.util.UUID;

/** A recurring rule was edited. Published in the transaction that edits it. */
public record RecurringRuleEdited(UUID workspaceId, UUID ruleId, UUID actorUserId) {}

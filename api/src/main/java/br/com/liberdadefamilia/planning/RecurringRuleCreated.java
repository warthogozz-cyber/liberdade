package br.com.liberdadefamilia.planning;

import java.util.UUID;

/** A recurring rule was created. Published in the transaction that creates it. */
public record RecurringRuleCreated(UUID workspaceId, UUID ruleId, UUID actorUserId) {}

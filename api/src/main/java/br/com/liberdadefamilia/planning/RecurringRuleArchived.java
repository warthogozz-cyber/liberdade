package br.com.liberdadefamilia.planning;

import java.util.UUID;

/**
 * A recurring rule was archived; its open planned transactions from today on are gone. Published in the transaction
 * that archives it.
 */
public record RecurringRuleArchived(UUID workspaceId, UUID ruleId, UUID actorUserId) {}

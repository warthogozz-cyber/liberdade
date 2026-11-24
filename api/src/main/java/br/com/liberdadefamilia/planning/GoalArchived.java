package br.com.liberdadefamilia.planning;

import java.util.UUID;

/** A goal of the family was archived. Published in the transaction that archives it, and only the first time. */
public record GoalArchived(UUID workspaceId, UUID goalId, UUID actorUserId) {}

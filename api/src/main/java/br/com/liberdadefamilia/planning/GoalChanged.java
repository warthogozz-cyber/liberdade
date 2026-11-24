package br.com.liberdadefamilia.planning;

import java.util.UUID;

/**
 * A goal was created, edited, had money put in or taken out, or was marked reached. Published inside the transaction
 * that wrote it.
 */
public record GoalChanged(UUID workspaceId, UUID goalId) {}

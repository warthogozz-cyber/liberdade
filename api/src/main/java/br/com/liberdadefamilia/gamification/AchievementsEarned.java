package br.com.liberdadefamilia.gamification;

import java.util.UUID;

/**
 * A member earned at least one achievement outside of a silent evaluation. Published inside the transaction that wrote
 * the rows; it carries no content, only who to tell.
 */
public record AchievementsEarned(UUID workspaceId, UUID membershipId) {}

package br.com.liberdadefamilia.planning.internal;

import java.util.List;

/**
 * The family's goals that are not archived: the active ones first, oldest first, then the reached ones, latest first.
 * {@code limitReached} says the Grátis limit stops a new one; it is always false for the youth and the viewer, who
 * cannot create any.
 */
record GoalsView(List<GoalView> goals, boolean limitReached) {}

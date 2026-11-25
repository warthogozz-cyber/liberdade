package br.com.liberdadefamilia.gamification.domain;

/**
 * A weekly streak: the weeks of the live run, how many of them were frozen (no entry, forgiven once per month) and the
 * longest run ever.
 */
public record Streak(int weeks, int frozenWeeks, int longest) {}

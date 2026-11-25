package br.com.liberdadefamilia.gamification;

import java.time.Instant;

/** An achievement as the data export carries it: the key is the enum's name. */
public record ExportedAchievement(String key, Instant earnedAt) {}

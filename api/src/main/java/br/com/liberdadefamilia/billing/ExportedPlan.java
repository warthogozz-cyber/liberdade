package br.com.liberdadefamilia.billing;

import java.time.Instant;

/** The stored plan of a family, as it is, whether it still runs or not. */
public record ExportedPlan(String key, boolean founder, Instant startsAt, Instant endsAt) {}

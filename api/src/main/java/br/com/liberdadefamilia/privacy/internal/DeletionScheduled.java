package br.com.liberdadefamilia.privacy.internal;

import java.time.Instant;

/** The day a deletion request takes effect, unless it is cancelled first. */
record DeletionScheduled(Instant deletionScheduledFor) {}

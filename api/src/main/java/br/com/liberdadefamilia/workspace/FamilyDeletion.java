package br.com.liberdadefamilia.workspace;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** A family's deletion request and its cancellation (D25). The purge itself is {@code FamilyPurgeJob}. */
public interface FamilyDeletion {

    /** How long a request waits before the family is purged. */
    Duration GRACE = Duration.ofDays(30);

    /**
     * Marks the family for deletion and returns the day it goes. A repeat answers the same instant and publishes
     * nothing.
     */
    Instant schedule(UUID workspaceId, UUID actorUserId);

    /** Takes the request back. Nothing pending: nothing happens and nothing is published. */
    void cancel(UUID workspaceId, UUID actorUserId);
}

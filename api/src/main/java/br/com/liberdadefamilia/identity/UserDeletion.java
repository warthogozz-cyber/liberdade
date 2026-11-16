package br.com.liberdadefamilia.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Asking for an account to be deleted, taking the request back, and erasing the account once the grace is over. */
public interface UserDeletion {

    /** How long a request waits before the account is erased (D23). */
    Duration GRACE = Duration.ofDays(30);

    /**
     * Marks the account for deletion and returns the day it will be erased: the request plus {@link #GRACE}. Asking
     * again keeps the first request, so the date does not move.
     */
    Instant schedule(UUID userId);

    /** Takes the request back. Nothing happens when none is pending. */
    void cancel(UUID userId);

    /** The accounts whose request is more than {@link #GRACE} old at {@code now}. */
    List<UUID> dueForPurge(Instant now);

    /**
     * Erases the account: the passkey entity (its credentials go with it), the sessions, then the user, which takes
     * the sign-in identities and the person's own consents along and clears the person from every other table.
     * Memberships are ended by the caller first, since they stay behind.
     */
    void purge(UUID userId);
}

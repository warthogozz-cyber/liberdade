package br.com.liberdadefamilia.audit;

import java.util.List;
import java.util.UUID;

/** Records sensitive actions and reads back what concerns a person. */
public interface AuditTrail {

    /** Stores the entry, in the caller's transaction when there is one, so a failed action leaves no row. */
    void record(AuditEntry entry);

    /**
     * The person's history of the last 12 months, newest first: what they did, what was done to their account and what
     * was done to any of their memberships, active or not. At most 100 items, whatever {@code limit} says.
     */
    List<ActivityItem> activityOf(UUID userId, int limit);

    /** The same history as {@link #activityOf}, with no cap on the number of items: for the person's data export. */
    List<ActivityItem> allActivityOf(UUID userId);
}

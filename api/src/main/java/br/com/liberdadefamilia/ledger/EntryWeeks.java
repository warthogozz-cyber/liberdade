package br.com.liberdadefamilia.ledger;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.SortedSet;
import java.util.UUID;

/** Which weeks a member wrote something down, for the weekly streak and the first-entry achievement. */
public interface EntryWeeks {

    /**
     * The Mondays of the weeks, in the zone, in which the membership entered at least one confirmed transaction. A
     * planned one, a deleted one and one a recurring rule made do not count.
     */
    SortedSet<LocalDate> mondaysWithEntries(UUID workspaceId, UUID membershipId, ZoneId zone);
}

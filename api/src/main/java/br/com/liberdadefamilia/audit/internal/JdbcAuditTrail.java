package br.com.liberdadefamilia.audit.internal;

import br.com.liberdadefamilia.audit.ActivityItem;
import br.com.liberdadefamilia.audit.AuditEntry;
import br.com.liberdadefamilia.audit.AuditTrail;
import br.com.liberdadefamilia.identity.UserDirectory;
import br.com.liberdadefamilia.identity.UserSummary;
import br.com.liberdadefamilia.workspace.MemberSummary;
import br.com.liberdadefamilia.workspace.Memberships;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class JdbcAuditTrail implements AuditTrail {

    static final int MAX_ITEMS = 100;
    static final int KEEP_MONTHS = 12;

    private record Key(@Nullable UUID workspaceId, UUID userId) {}

    private final AuditRepository repository;
    private final Memberships memberships;
    private final UserDirectory users;
    private final Clock clock;

    JdbcAuditTrail(AuditRepository repository, Memberships memberships, UserDirectory users, Clock clock) {
        this.repository = repository;
        this.memberships = memberships;
        this.users = users;
        this.clock = clock;
    }

    @Override
    public void record(AuditEntry entry) {
        repository.insert(entry);
    }

    @Override
    public List<ActivityItem> activityOf(UUID userId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return history(userId, Math.min(limit, MAX_ITEMS));
    }

    @Override
    public List<ActivityItem> allActivityOf(UUID userId) {
        return history(userId, null);
    }

    /** The last {@value #KEEP_MONTHS} months, newest first; every item when {@code limit} is null. */
    private List<ActivityItem> history(UUID userId, @Nullable Integer limit) {
        List<UUID> membershipIds = memberships.membershipsOf(userId).stream()
                .map(MemberSummary::membershipId)
                .toList();
        OffsetDateTime since =
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusMonths(KEEP_MONTHS);
        List<AuditRepository.Row> rows = repository.activityOf(userId, membershipIds, since, limit);
        Map<Key, String> names = actorNames(rows);
        return rows.stream()
                .map(row -> new ActivityItem(
                        row.id(),
                        row.action(),
                        row.occurredAt(),
                        row.workspaceId(),
                        row.actorUserId() == null ? null : names.get(new Key(row.workspaceId(), row.actorUserId())),
                        userId.equals(row.actorUserId()),
                        row.metadata()))
                .toList();
    }

    /** The nickname in the event's family, else the display name; resolved for all rows with two queries at most. */
    private Map<Key, String> actorNames(List<AuditRepository.Row> rows) {
        Set<UUID> workspaceIds = new HashSet<>();
        Set<UUID> actorIds = new HashSet<>();
        for (AuditRepository.Row row : rows) {
            if (row.actorUserId() != null) {
                actorIds.add(row.actorUserId());
                if (row.workspaceId() != null) {
                    workspaceIds.add(row.workspaceId());
                }
            }
        }
        if (actorIds.isEmpty()) {
            return Map.of();
        }
        Map<Key, String> nicknames = new HashMap<>();
        if (!workspaceIds.isEmpty()) {
            for (MemberSummary member : memberships.membersOf(workspaceIds, actorIds)) {
                if (member.userId() != null && member.nickname() != null) {
                    nicknames.put(new Key(member.workspaceId(), member.userId()), member.nickname());
                }
            }
        }
        Map<UUID, UserSummary> people = users.findAll(actorIds);
        Map<Key, String> names = new HashMap<>();
        for (AuditRepository.Row row : rows) {
            UUID actor = row.actorUserId();
            if (actor == null) {
                continue;
            }
            Key key = new Key(row.workspaceId(), actor);
            String nickname = nicknames.get(key);
            UserSummary person = people.get(actor);
            String name = nickname != null ? nickname : person == null ? null : person.displayName();
            if (name != null) {
                names.put(key, name);
            }
        }
        return names;
    }
}

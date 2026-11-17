package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.identity.UserDirectory;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The family's active Owners: an active OWNER membership whose person has not asked for their account to be deleted.
 *
 * <p>The rule that a family always keeps one is checked with the Owners' rows locked, so two requests (stepping down,
 * leaving, asking for the account to be deleted) cannot both pass it. A caller acting for a person has locked that
 * person's user row before, the lock order of the app (user first, then memberships by id). The rows are locked in id order, the same order
 * for every caller, and who is leaving is read afterwards in a statement of its own: after waiting on the lock, that
 * read sees what the other transaction committed.
 */
@Component
class ActiveOwners {

    /** An Owner whose person keeps their account. */
    record Owner(UUID workspaceId, UUID membershipId, UUID userId) {}

    private final JdbcClient jdbc;
    private final UserDirectory users;

    ActiveOwners(JdbcClient jdbc, UserDirectory users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    /** Locks the active OWNER rows of these families until the transaction ends, and answers the active Owners. */
    @Transactional(propagation = Propagation.MANDATORY)
    List<Owner> lock(Collection<UUID> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        List<Owner> locked = jdbc.sql("""
                        select workspace_id, id, user_id from memberships
                        where workspace_id in (:workspaceIds) and role = 'OWNER' and status = 'ACTIVE'
                          and user_id is not null
                        order by id
                        for update""")
                .param("workspaceIds", workspaceIds)
                .query((rs, row) -> new Owner(
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class)))
                .list();
        Set<UUID> leaving = users.pendingDeletion(
                locked.stream().map(Owner::userId).distinct().toList());
        return locked.stream()
                .filter(owner -> !leaving.contains(owner.userId()))
                .toList();
    }
}

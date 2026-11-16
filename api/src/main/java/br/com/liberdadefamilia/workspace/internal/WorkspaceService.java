package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.workspace.WorkspaceCreated;
import br.com.liberdadefamilia.workspace.internal.WorkspaceRepository.NewWorkspace;
import java.time.ZoneId;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates families. */
@Service
class WorkspaceService {

    private final WorkspaceRepository workspaces;
    private final ApplicationEventPublisher events;

    WorkspaceService(WorkspaceRepository workspaces, ApplicationEventPublisher events) {
        this.workspaces = workspaces;
        this.events = events;
    }

    /**
     * Creates the user's family with them as OWNER and publishes {@link WorkspaceCreated}, all in one transaction: if a
     * listener fails, nothing is kept.
     *
     * @throws WorkspaceAlreadyExistsException when the user already belongs to a family, including when another request
     *     for the same user commits first and the partial unique index refuses this membership
     */
    @Transactional
    UUID create(CurrentUser user, String name, ZoneId timezone) {
        if (workspaces.hasActiveMembership(user.id())) {
            throw new WorkspaceAlreadyExistsException();
        }
        NewWorkspace workspace = workspaces.insert(name, timezone);
        UUID membershipId;
        try {
            membershipId = workspaces.insertOwner(workspace.id(), user.id(), firstName(user.displayName()));
        } catch (DuplicateKeyException anotherRequestWon) {
            throw new WorkspaceAlreadyExistsException();
        }
        events.publishEvent(new WorkspaceCreated(workspace.id(), membershipId, workspace.baseCurrency()));
        return workspace.id();
    }

    /** The first word of the display name (D5), or null when it has none. */
    static @Nullable String firstName(String displayName) {
        String first = displayName.strip().split("\\s+", 2)[0];
        return first.isEmpty() ? null : first;
    }
}

package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.workspace.WorkspaceCreated;
import br.com.liberdadefamilia.workspace.WorkspaceMember;
import br.com.liberdadefamilia.workspace.WorkspaceRenamed;
import br.com.liberdadefamilia.workspace.internal.WorkspaceRepository.NewWorkspace;
import java.time.ZoneId;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and renames families. */
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
     * @throws WorkspaceAlreadyExistsException when the user already owns a family. The user's lock comes first, so when
     *     another request for the same user is creating one, this one waits and then finds it.
     */
    @Transactional
    UUID create(CurrentUser user, String name, ZoneId timezone) {
        workspaces.lockFamilyCreation(user.id());
        if (workspaces.isActiveOwnerOfAnyFamily(user.id())) {
            throw new WorkspaceAlreadyExistsException();
        }
        NewWorkspace workspace = workspaces.insert(name, timezone);
        UUID membershipId = workspaces.insertOwner(workspace.id(), user.id(), firstName(user.displayName()));
        events.publishEvent(new WorkspaceCreated(workspace.id(), membershipId, workspace.baseCurrency()));
        return workspace.id();
    }

    /**
     * Gives the family its new name and publishes {@link WorkspaceRenamed}, and returns the family as the caller sees
     * it.
     */
    @Transactional
    MeWorkspace rename(WorkspaceMember member, String name) {
        workspaces.rename(member.workspaceId(), name);
        events.publishEvent(new WorkspaceRenamed(member.workspaceId(), member.userId()));
        return workspaces
                .activeWorkspaceOf(member.userId(), member.workspaceId())
                .orElseThrow(WorkspaceNotFoundException::new);
    }

    /** The first word of the display name (D5), or null when it has none. */
    static @Nullable String firstName(String displayName) {
        String first = displayName.strip().split("\\s+", 2)[0];
        return first.isEmpty() ? null : first;
    }
}

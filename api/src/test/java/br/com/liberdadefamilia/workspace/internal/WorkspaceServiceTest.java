package br.com.liberdadefamilia.workspace.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.liberdadefamilia.identity.CurrentUser;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

/** The order that makes a double tap safe: the user's lock comes before the owner check. */
class WorkspaceServiceTest {

    private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final WorkspaceService service = new WorkspaceService(workspaces, events);
    private final CurrentUser user = new CurrentUser(UUID.randomUUID(), "Ana Teste", null, null);

    @Test
    void locksBeforeOwnerCheck() {
        when(workspaces.isActiveOwnerOfAnyFamily(any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(user, "Família Teste", ZoneId.of("America/Fortaleza")))
                .isInstanceOf(WorkspaceAlreadyExistsException.class);

        InOrder order = inOrder(workspaces);
        order.verify(workspaces).lockFamilyCreation(user.id());
        order.verify(workspaces).isActiveOwnerOfAnyFamily(user.id());
        verifyNoInteractions(events);
    }

    @Test
    void nicknameIsTheFirstWord() {
        assertThat(WorkspaceService.firstName("  Ana  Maria Teste ")).isEqualTo("Ana");
        assertThat(WorkspaceService.firstName("   ")).isNull();
    }
}

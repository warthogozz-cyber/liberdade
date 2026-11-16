package br.com.liberdadefamilia.workspace.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.liberdadefamilia.identity.CurrentUser;
import br.com.liberdadefamilia.workspace.internal.WorkspaceRepository.NewWorkspace;
import java.time.ZoneId;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

/**
 * The losing side of a double tap, made deterministic: {@code WorkspaceIT.concurrentCreateMakesOneFamily} reaches it
 * only when both requests pass the first check before either commits.
 */
class WorkspaceServiceTest {

    private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final WorkspaceService service = new WorkspaceService(workspaces, events);
    private final CurrentUser user = new CurrentUser(UUID.randomUUID(), "Ana Teste", null);

    @Test
    void membershipRefusedByTheIndexIsAlreadyExists() {
        when(workspaces.insert(any(), any()))
                .thenReturn(new NewWorkspace(UUID.randomUUID(), Currency.getInstance("BRL")));
        when(workspaces.insertOwner(any(), any(), any())).thenThrow(new DuplicateKeyException("one active family"));

        assertThatThrownBy(() -> service.create(user, "Família Teste", ZoneId.of("America/Fortaleza")))
                .isInstanceOf(WorkspaceAlreadyExistsException.class);
        verifyNoInteractions(events);
    }

    @Test
    void nicknameIsTheFirstWord() {
        assertThat(WorkspaceService.firstName("  Ana  Maria Teste ")).isEqualTo("Ana");
        assertThat(WorkspaceService.firstName("   ")).isNull();
    }
}

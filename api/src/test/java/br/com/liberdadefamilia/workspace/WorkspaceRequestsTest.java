package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WorkspaceRequestsTest {

    @Test
    void memberIsEmptyWithoutGuard() {
        assertThat(WorkspaceRequests.member(new MockHttpServletRequest())).isEqualTo(Optional.empty());
    }

    @Test
    void memberReturnsWhatTheGuardAttached() {
        var member = new WorkspaceMember(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Role.OWNER,
                ZoneId.of("America/Sao_Paulo"),
                Currency.getInstance("BRL"));
        var request = new MockHttpServletRequest();

        WorkspaceRequests.attach(request, member);

        assertThat(WorkspaceRequests.member(request)).isEqualTo(Optional.of(member));
    }
}

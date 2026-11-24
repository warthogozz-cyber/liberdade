package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/** Which of some memberships are still ACTIVE, in one query. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MembershipsActiveAmongIT {

    @Autowired
    Memberships memberships;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Test
    void removedIsNotActive() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID anaMembership = workspaces.membershipOf(ws, ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bia");
        memberships.remove(ws, biaMembership, null, MemberRemoved.Reason.BY_OWNER);

        assertThat(memberships.activeAmong(List.of(anaMembership, biaMembership, UUID.randomUUID())))
                .containsExactly(anaMembership);
        assertThat(memberships.activeAmong(Set.of())).isEmpty();
    }
}

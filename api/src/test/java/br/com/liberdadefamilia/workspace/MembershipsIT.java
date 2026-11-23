package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The membership lookups that other modules use to act as a member of the family. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MembershipsIT {

    @Autowired
    Memberships memberships;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @Test
    void activeMemberAndOwner() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID bia = users.create("Bia Teste", "bia@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID anaMembership = workspaces.membershipOf(ws, ana);
        UUID biaMembership = workspaces.addMember(ws, bia, Role.ADULT, "Bia");
        jdbc.sql("update workspaces set timezone = 'America/Manaus' where id = :ws")
                .param("ws", ws)
                .update();

        WorkspaceMember member = memberships.activeMember(ws, biaMembership).orElseThrow();

        assertThat(member.membershipId()).isEqualTo(biaMembership);
        assertThat(member.userId()).isEqualTo(bia);
        assertThat(member.role()).isEqualTo(Role.ADULT);
        assertThat(member.timezone()).isEqualTo(ZoneId.of("America/Manaus"));
        assertThat(memberships.activeOwner(ws).orElseThrow().membershipId()).isEqualTo(anaMembership);

        memberships.remove(ws, biaMembership, null, MemberRemoved.Reason.BY_OWNER);

        assertThat(memberships.activeMember(ws, biaMembership)).isEmpty();
        assertThat(memberships.activeMember(UUID.randomUUID(), anaMembership)).isEmpty();
    }

    @Test
    void activeOwnerIsTheOldestAndSkipsRemovedOnes() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID caio = users.create("Caio Teste", "caio@example.com");
        UUID ws = workspaces.createFor(ana);
        UUID anaMembership = workspaces.membershipOf(ws, ana);
        UUID caioMembership = workspaces.addMember(ws, caio, Role.OWNER, "Caio");

        assertThat(memberships.activeOwner(ws).orElseThrow().membershipId()).isEqualTo(anaMembership);

        memberships.remove(ws, anaMembership, null, MemberRemoved.Reason.BY_OWNER);

        assertThat(memberships.activeOwner(ws).orElseThrow().membershipId()).isEqualTo(caioMembership);
        memberships.remove(ws, caioMembership, null, MemberRemoved.Reason.BY_OWNER);
        assertThat(memberships.activeOwner(ws)).isEmpty();
    }
}

package br.com.liberdadefamilia.support;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.workspace.Role;
import com.jayway.jsonpath.JsonPath;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Sets up families for tests. {@link #createFor} goes through {@code POST /v1/workspaces}, so whatever listens to
 * {@code WorkspaceCreated} runs too; the other two work straight in the database.
 */
public class TestWorkspaces {

    // Resolved on first use: ITs without MockMvc share this context and never call createFor.
    private final ObjectProvider<MockMvcTester> mvc;
    private final JdbcClient jdbc;

    public TestWorkspaces(ObjectProvider<MockMvcTester> mvc, JdbcClient jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    /** Creates a family named "Família Teste" with the user as OWNER, and returns its id. */
    public UUID createFor(UUID userId) {
        MockMvcTester tester = mvc.getObject();
        MvcTestResult result = tester.post()
                .uri("/v1/workspaces")
                .with(TestUsers.signedIn(userId, null))
                .with(CsrfSession.start(tester, TestUsers.signedIn(userId, null)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Família Teste\"}")
                .exchange();
        assertThat(result).hasStatus(201);
        try {
            return UUID.fromString(
                    JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id"));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Adds an ACTIVE membership with the given role and returns its id. */
    public UUID addMember(UUID workspaceId, UUID userId, Role role) {
        return jdbc.sql("""
                        insert into memberships (workspace_id, user_id, role)
                        values (:workspaceId, :userId, :role)
                        returning id""")
                .param("workspaceId", workspaceId)
                .param("userId", userId)
                .param("role", role.name())
                .query(UUID.class)
                .single();
    }

    public UUID membershipOf(UUID workspaceId, UUID userId) {
        return jdbc.sql("select id from memberships where workspace_id = :workspaceId and user_id = :userId")
                .param("workspaceId", workspaceId)
                .param("userId", userId)
                .query(UUID.class)
                .single();
    }
}

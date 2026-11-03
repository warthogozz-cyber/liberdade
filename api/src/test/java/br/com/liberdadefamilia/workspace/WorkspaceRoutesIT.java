package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The guard covers {@code /v1/workspaces/*}{@code /**} and reads the family from {@code {ws}}. A {@link WorkspaceScoped}
 * route mapped anywhere else would go unguarded, and a route below {@code /v1/workspaces/} that names its family
 * segment otherwise would always answer 404. Same configuration as the other workspace ITs, so it reuses their context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WorkspaceRoutesIT {

    private static final Pattern FAMILY_ROUTE = Pattern.compile("/v1/workspaces/\\{ws}(/.*)?");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void workspaceScopedRoutesLiveUnderTheGuard() {
        assertThat(routes(handler -> true)).as("the walk sees the real routes").contains("/v1/workspaces", "/v1/me");
        assertThat(routes(handler -> handler.hasMethodAnnotation(WorkspaceScoped.class))
                        .filter(pattern -> !FAMILY_ROUTE.matcher(pattern).matches()))
                .as("@WorkspaceScoped routes outside /v1/workspaces/{ws}")
                .isEmpty();
    }

    @Test
    void routesBelowWorkspacesNameTheFamilyWs() {
        assertThat(routes(handler -> true)
                        .filter(pattern -> pattern.startsWith("/v1/workspaces/"))
                        .filter(pattern -> !FAMILY_ROUTE.matcher(pattern).matches()))
                .as("routes below /v1/workspaces/ whose family segment is not {ws}")
                .isEmpty();
    }

    private Stream<String> routes(Predicate<HandlerMethod> handlers) {
        return mappings.getHandlerMethods().entrySet().stream()
                .filter(handler -> handlers.test(handler.getValue()))
                .flatMap(handler -> handler.getKey().getPatternValues().stream());
    }
}

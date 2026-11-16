package br.com.liberdadefamilia.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every real route under {@code /v1/workspaces/{ws}} declares who may use it. The guard already answers 403 to one
 * that does not; this catches the omission at build time instead. Same configuration as the other workspace ITs, so it
 * reuses their context (and leaves out the probe routes of {@link WorkspaceGuardIT}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WorkspaceGuardCoverageIT {

    private static final String FAMILY_PREFIX = "/v1/workspaces/{ws}";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void everyFamilyRouteIsWorkspaceScoped() {
        List<Map.Entry<RequestMappingInfo, HandlerMethod>> familyRoutes =
                mappings.getHandlerMethods().entrySet().stream()
                        .filter(handler -> handler.getKey().getPatternValues().stream()
                                .anyMatch(pattern -> pattern.startsWith(FAMILY_PREFIX)))
                        .toList();

        assertThat(familyRoutes).as("routes under %s", FAMILY_PREFIX).hasSizeGreaterThanOrEqualTo(5);
        assertThat(familyRoutes)
                .as("routes under %s without @WorkspaceScoped", FAMILY_PREFIX)
                .allSatisfy(handler -> assertThat(handler.getValue().hasMethodAnnotation(WorkspaceScoped.class))
                        .as("%s", handler.getKey())
                        .isTrue());
    }
}

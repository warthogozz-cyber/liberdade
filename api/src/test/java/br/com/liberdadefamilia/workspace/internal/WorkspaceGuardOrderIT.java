package br.com.liberdadefamilia.workspace.internal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.workspace.WorkspaceRequests;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** The family guard has a fixed order, so another module's guard can run right after it and read the member. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, WorkspaceGuardOrderIT.Neighbours.class})
class WorkspaceGuardOrderIT {

    private static final HandlerInterceptor BEFORE = new HandlerInterceptor() {};
    private static final HandlerInterceptor AFTER = new HandlerInterceptor() {};

    @TestConfiguration
    static class Neighbours implements WebMvcConfigurer {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(AFTER)
                    .addPathPatterns("/v1/workspaces/*/**")
                    .order(WorkspaceRequests.GUARD_ORDER + 100);
            registry.addInterceptor(BEFORE)
                    .addPathPatterns("/v1/workspaces/*/**")
                    .order(WorkspaceRequests.GUARD_ORDER - 1);
        }
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void guardIsRegisteredWithOrder100() throws Exception {
        var request = new MockHttpServletRequest("GET", "/v1/workspaces/" + UUID.randomUUID() + "/modules");

        var chain = mappings.getHandler(request);

        assertThat(chain).isNotNull();
        List<HandlerInterceptor> interceptors = chain.getInterceptorList();
        int guard = indexOfGuard(interceptors);
        assertThat(WorkspaceRequests.GUARD_ORDER).isEqualTo(100);
        assertThat(interceptors.indexOf(BEFORE)).isLessThan(guard);
        assertThat(interceptors.indexOf(AFTER)).isGreaterThan(guard);
    }

    private static int indexOfGuard(List<HandlerInterceptor> interceptors) {
        for (int i = 0; i < interceptors.size(); i++) {
            if (interceptors.get(i) instanceof WorkspaceGuardInterceptor) {
                return i;
            }
        }
        throw new AssertionError("family guard not in chain: " + interceptors);
    }
}

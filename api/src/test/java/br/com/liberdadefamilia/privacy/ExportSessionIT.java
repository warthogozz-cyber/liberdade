package br.com.liberdadefamilia.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import br.com.liberdadefamilia.support.TestUsers;
import br.com.liberdadefamilia.support.TestWorkspaces;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The session is saved when the response is committed. A JDBC session is not thread-safe, so the export must not
 * commit the response from another thread than the request's: a body streamed from the async executor flushes (and so
 * saves the session) there while the request thread saves it again as it unwinds, and the two walk the same pending
 * changes (a ConcurrentModificationException, an HTTP 500, once in a while).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExportSessionIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestUsers users;

    @Autowired
    TestWorkspaces workspaces;

    @MockitoSpyBean
    JdbcIndexedSessionRepository sessions;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void theSessionIsSavedOnTheRequestThreadOnly() {
        UUID ana = users.create("Ana Teste", "ana@example.com");
        UUID ws = workspaces.createFor(ana);
        List<Thread> savedOn = new CopyOnWriteArrayList<>();
        doAnswer(call -> {
                    savedOn.add(Thread.currentThread());
                    return call.callRealMethod();
                })
                .when((SessionRepository) sessions)
                .save(any());
        RequestPostProcessor session = TestUsers.signedIn(ana, null);

        MvcTestResult mine = mvc.post()
                .uri("/v1/me/export")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();
        MvcTestResult family = mvc.post()
                .uri("/v1/workspaces/" + ws + "/export")
                .with(session)
                .with(CsrfSession.start(mvc, session))
                .exchange();

        assertThat(mine).hasStatusOk();
        assertThat(family).hasStatusOk();
        assertThat(savedOn).isNotEmpty().containsOnly(Thread.currentThread());
    }
}

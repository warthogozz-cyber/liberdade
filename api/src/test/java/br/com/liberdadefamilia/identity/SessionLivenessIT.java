package br.com.liberdadefamilia.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.liberdadefamilia.TestcontainersConfiguration;
import br.com.liberdadefamilia.support.CsrfSession;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Which of some session ids still name a session that has not expired or been deleted. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SessionLivenessIT {

    @Autowired
    SessionLiveness liveness;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void aliveAndExpired() {
        String open = CsrfSession.start(mvc).sessionId();
        String expired = CsrfSession.start(mvc).sessionId();
        jdbc.sql("update spring_session set expiry_time = 1 where session_id = :id")
                .param("id", expired)
                .update();

        assertThat(liveness.aliveAmong(List.of(open, expired))).containsExactly(open);
    }

    @Test
    void deletedSessionIsNotAlive() {
        String gone = CsrfSession.start(mvc).sessionId();
        jdbc.sql("delete from spring_session where session_id = :id")
                .param("id", gone)
                .update();

        assertThat(liveness.aliveAmong(List.of(gone, "never-existed"))).isEmpty();
    }

    @Test
    void noIdsMeansNoneAlive() {
        assertThat(liveness.aliveAmong(Set.of())).isEmpty();
    }
}

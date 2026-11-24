package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.SessionLiveness;
import java.time.Clock;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcSessionLiveness implements SessionLiveness {

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcSessionLiveness(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Set<String> aliveAmong(Collection<String> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(
                jdbc.sql("select session_id from spring_session where session_id in (:ids) and expiry_time > :now")
                        .param("ids", sessionIds)
                        .param("now", clock.millis())
                        .query(String.class)
                        .list());
    }
}

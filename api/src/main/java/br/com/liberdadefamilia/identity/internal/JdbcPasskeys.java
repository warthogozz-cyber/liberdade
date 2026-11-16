package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.Passkeys;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads the tables Spring Security's passkey repositories write. */
@Component
class JdbcPasskeys implements Passkeys {

    private final JdbcClient jdbc;

    JdbcPasskeys(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@code created} has no time zone: read it as a {@link Timestamp}, the way Spring's repository wrote it, so the
     * instant comes back unchanged.
     */
    @Override
    public Optional<Instant> latestCreatedAt(UUID userId) {
        return jdbc.sql("""
                        select max(c.created)
                          from user_credentials c
                          join user_entities e on e.id = c.user_entity_user_id
                         where e.name = :name
                        """)
                .param("name", userId.toString())
                .query((rs, row) -> Optional.ofNullable(rs.getTimestamp(1)).map(Timestamp::toInstant))
                .single();
    }
}

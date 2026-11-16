package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.PasskeyRemoved;
import br.com.liberdadefamilia.identity.PasskeySummary;
import br.com.liberdadefamilia.identity.Passkeys;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reads the tables Spring Security's passkey repositories write. */
@Component
class JdbcPasskeys implements Passkeys {

    private final JdbcClient jdbc;

    private final ApplicationEventPublisher events;

    JdbcPasskeys(JdbcClient jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
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

    @Override
    @Transactional(readOnly = true)
    public List<PasskeySummary> list(UUID userId) {
        return jdbc.sql("""
                        select c.credential_id, c.label, c.created, c.last_used
                          from user_credentials c
                          join user_entities e on e.id = c.user_entity_user_id
                         where e.name = :name
                         order by c.created desc, c.credential_id
                        """)
                .param("name", userId.toString())
                .query((rs, row) -> new PasskeySummary(
                        rs.getString("credential_id"),
                        rs.getString("label"),
                        rs.getTimestamp("created").toInstant(),
                        Optional.ofNullable(rs.getTimestamp("last_used"))
                                .map(Timestamp::toInstant)
                                .orElse(null)))
                .list();
    }

    @Override
    @Transactional
    public void remove(UUID userId, String credentialId) {
        List<String> owned = jdbc.sql("""
                        select c.credential_id
                          from user_credentials c
                          join user_entities e on e.id = c.user_entity_user_id
                         where e.name = :name
                           for update of c
                        """)
                .param("name", userId.toString())
                .query(String.class)
                .list();
        if (!owned.contains(credentialId)) {
            throw new PasskeyNotFoundException();
        }
        if (owned.size() == 1 && !hasGoogle(userId)) {
            throw new LastSignInMethodException();
        }
        jdbc.sql("delete from user_credentials where credential_id = :id")
                .param("id", credentialId)
                .update();
        events.publishEvent(new PasskeyRemoved(userId));
    }

    private boolean hasGoogle(UUID userId) {
        return jdbc.sql("select exists (select 1 from auth_identities where user_id = :id and provider = 'google')")
                .param("id", userId)
                .query(Boolean.class)
                .single();
    }
}

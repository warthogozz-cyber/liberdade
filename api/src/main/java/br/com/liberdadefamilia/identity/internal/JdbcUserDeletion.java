package br.com.liberdadefamilia.identity.internal;

import br.com.liberdadefamilia.identity.UserDeletion;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcUserDeletion implements UserDeletion {

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcUserDeletion(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Scheduled schedule(UUID userId) {
        int changed = jdbc.sql("""
                        update users
                        set deletion_requested_at = :now, updated_at = :now, version = version + 1
                        where id = :id and deletion_requested_at is null""")
                .param("now", utc(clock.instant()))
                .param("id", userId)
                .update();
        Instant requested = jdbc.sql("select deletion_requested_at from users where id = :id")
                .param("id", userId)
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
        return new Scheduled(requested.plus(GRACE), changed == 1);
    }

    @Override
    @Transactional
    public boolean cancel(UUID userId) {
        return jdbc.sql("""
                        update users
                        set deletion_requested_at = null, updated_at = :now, version = version + 1
                        where id = :id and deletion_requested_at is not null""")
                        .param("now", utc(clock.instant()))
                        .param("id", userId)
                        .update()
                == 1;
    }

    @Override
    public List<UUID> dueForPurge(Instant now) {
        return jdbc.sql("""
                        select id from users
                        where deletion_requested_at is not null and deletion_requested_at < :cutoff
                        order by deletion_requested_at, id""")
                .param("cutoff", utc(now.minus(GRACE)))
                .query(UUID.class)
                .list();
    }

    @Override
    public boolean lockIfDue(UUID userId, Instant now) {
        return !jdbc.sql("""
                        select id from users
                        where id = :id and deletion_requested_at is not null and deletion_requested_at < :cutoff
                        for update""")
                .param("id", userId)
                .param("cutoff", utc(now.minus(GRACE)))
                .query(UUID.class)
                .list()
                .isEmpty();
    }

    @Override
    @Transactional
    public void purge(UUID userId) {
        // user_entities.name holds the user id (V2); the credentials go with the entity.
        jdbc.sql("delete from user_entities where name = :name")
                .param("name", userId.toString())
                .update();
        // The session attributes go with their session.
        jdbc.sql("delete from spring_session where principal_name = :name")
                .param("name", userId.toString())
                .update();
        jdbc.sql("delete from users where id = :id").param("id", userId).update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}

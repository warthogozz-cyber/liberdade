package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.FamilyDeletion;
import br.com.liberdadefamilia.workspace.WorkspaceDeletionCancelled;
import br.com.liberdadefamilia.workspace.WorkspaceDeletionRequested;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcFamilyDeletion implements FamilyDeletion {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    JdbcFamilyDeletion(JdbcClient jdbc, Clock clock, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.events = events;
    }

    @Override
    @Transactional
    public Instant schedule(UUID workspaceId, UUID actorUserId) {
        int changed = jdbc.sql("""
                        update workspaces set deletion_requested_at = :now, updated_at = :now, version = version + 1
                        where id = :id and deletion_requested_at is null""")
                .param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .param("id", workspaceId)
                .update();
        if (changed == 1) {
            events.publishEvent(new WorkspaceDeletionRequested(workspaceId, actorUserId));
        }
        return jdbc.sql("select deletion_requested_at from workspaces where id = :id")
                .param("id", workspaceId)
                .query(OffsetDateTime.class)
                .single()
                .toInstant()
                .plus(GRACE);
    }

    @Override
    @Transactional
    public void cancel(UUID workspaceId, UUID actorUserId) {
        int changed = jdbc.sql("""
                        update workspaces set deletion_requested_at = null, updated_at = :now, version = version + 1
                        where id = :id and deletion_requested_at is not null""")
                .param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .param("id", workspaceId)
                .update();
        if (changed == 1) {
            events.publishEvent(new WorkspaceDeletionCancelled(workspaceId, actorUserId));
        }
    }
}

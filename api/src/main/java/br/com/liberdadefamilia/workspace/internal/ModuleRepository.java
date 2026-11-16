package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.ModuleToggled;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The family's module switches. No row means off. */
@Component
class ModuleRepository {

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    ModuleRepository(JdbcClient jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    boolean isEnabled(UUID workspaceId, ModuleKey key) {
        return jdbc.sql("""
                        select exists (
                            select 1
                            from workspace_modules
                            where workspace_id = :workspaceId and module_key = :key and enabled)""")
                .param("workspaceId", workspaceId)
                .param("key", key.wire())
                .query(Boolean.class)
                .single();
    }

    Set<ModuleKey> enabledKeys(UUID workspaceId) {
        Set<ModuleKey> enabled = EnumSet.noneOf(ModuleKey.class);
        jdbc.sql("select module_key from workspace_modules where workspace_id = :workspaceId and enabled")
                .param("workspaceId", workspaceId)
                .query(String.class)
                .list()
                .forEach(key -> ModuleKey.fromWire(key).ifPresent(enabled::add));
        return enabled;
    }

    /**
     * Stores the switch in one statement and publishes {@link ModuleToggled} inside the same transaction, only if the
     * effective value changed. The conflict update skips a row that already holds the value, so the statement itself
     * says whether it changed anything; a new row counts as a change only when it switches the module on, because off
     * is what the missing row already meant.
     */
    @Transactional
    void set(UUID workspaceId, ModuleKey key, boolean enabled, UUID actorUserId) {
        boolean changed = jdbc.sql("""
                        insert into workspace_modules (workspace_id, module_key, enabled, updated_by)
                        values (:workspaceId, :key, :enabled, :actor)
                        on conflict (workspace_id, module_key) do update
                            set enabled = excluded.enabled,
                                updated_by = excluded.updated_by,
                                updated_at = now()
                            where workspace_modules.enabled is distinct from excluded.enabled
                        returning (xmax = 0) as inserted, enabled""")
                .param("workspaceId", workspaceId)
                .param("key", key.wire())
                .param("enabled", enabled)
                .param("actor", actorUserId)
                .query((rs, row) -> !rs.getBoolean("inserted") || rs.getBoolean("enabled"))
                .optional()
                .orElse(false);
        if (changed) {
            events.publishEvent(new ModuleToggled(workspaceId, key, enabled, actorUserId));
        }
    }
}

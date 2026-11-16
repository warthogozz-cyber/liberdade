package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.ModuleKey;
import br.com.liberdadefamilia.workspace.Modules;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class JdbcModules implements Modules {

    private final ModuleRepository repository;

    JdbcModules(ModuleRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isEnabled(UUID workspaceId, ModuleKey key) {
        return repository.isEnabled(workspaceId, key);
    }
}

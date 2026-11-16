package br.com.liberdadefamilia.workspace;

import java.util.UUID;

/** Which optional modules a family has on. No stored row means off. */
public interface Modules {

    boolean isEnabled(UUID workspaceId, ModuleKey key);
}

package br.com.liberdadefamilia.workspace.internal;

import br.com.liberdadefamilia.workspace.ModuleKey;

/** One optional module and whether the family has it on. */
record ModuleView(ModuleKey key, boolean enabled) {}

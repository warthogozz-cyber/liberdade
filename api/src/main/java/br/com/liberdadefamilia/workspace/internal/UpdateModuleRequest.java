package br.com.liberdadefamilia.workspace.internal;

import jakarta.validation.constraints.NotNull;

/** Switches a module on or off. */
record UpdateModuleRequest(@NotNull Boolean enabled) {}

import type { ModuleKey } from '@/shared/api'

/** A module the app ships: `available` is false while its screens are not built yet. */
export type ModuleDefinition = { key: ModuleKey; available: boolean }

const definitions = new Map<ModuleKey, ModuleDefinition>()

/** Registers a module's definition; a second one for the same key replaces the first. */
export function registerModule(definition: ModuleDefinition): void {
  definitions.set(definition.key, definition)
}

/** The registered modules, in the order they were registered. */
export function listModules(): ModuleDefinition[] {
  return [...definitions.values()]
}

/** Empties the registry. For tests. */
export function resetModules(): void {
  definitions.clear()
}

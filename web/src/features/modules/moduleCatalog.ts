import type { ModuleKey } from '@/shared/api'
import { listModules } from '@/shared/modules'

/** The optional modules, in the order the screen lists them. Each one's text is `modules.<key>`. */
export const OPTIONAL_MODULES: readonly ModuleKey[] = [
  'planning',
  'indicators',
  'gamification',
  'ventures',
]

/**
 * Whether the module's screens exist. Every optional module is still to come (D14): one becomes
 * available when its own code calls `registerModule({ key, available: true })`.
 */
export function isAvailable(key: ModuleKey): boolean {
  return listModules().some((module) => module.key === key && module.available)
}

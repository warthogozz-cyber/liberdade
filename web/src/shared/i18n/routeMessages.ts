import { useSyncExternalStore } from 'react'
import { messages as baseMessages } from './messages.pt-BR'

/** Every message the app can show: the base ones and those of the routes loaded so far. */
export type AppMessages = Record<FormatjsIntl.Message['ids'], string>

// The routes' ids are typed in `formatjs.d.ts` before their bundles load, so what is merged now
// is typed as if every route had registered.
let merged = baseMessages as unknown as AppMessages
const listeners = new Set<() => void>()

/**
 * Adds a route's messages to the app's. A lazy route calls this when its module loads, so its text
 * stays out of the initial load. A bundle that is already merged changes nothing.
 */
export function registerMessages(bundle: Readonly<Record<string, string>>): void {
  const current: Readonly<Record<string, string>> = merged
  if (Object.entries(bundle).every(([id, text]) => current[id] === text)) return
  merged = { ...merged, ...bundle }
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** The messages as they are now. Components read `useAppMessages`, which follows registrations. */
export function appMessages(): AppMessages {
  return merged
}

/** The base messages plus every registered bundle; the one `IntlProvider` reads this. */
export function useAppMessages(): AppMessages {
  return useSyncExternalStore(subscribe, appMessages)
}

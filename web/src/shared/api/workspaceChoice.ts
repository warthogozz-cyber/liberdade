const KEY = 'liberdade.workspaceId'

const listeners = new Set<() => void>()

function notify(): void {
  listeners.forEach((listener) => listener())
}

/** The family this device chose, or `null` when none was chosen (or the storage is unavailable). */
export function readSelectedWorkspaceId(): string | null {
  try {
    return localStorage.getItem(KEY)
  } catch {
    return null
  }
}

/** Remembers the family chosen on this device and tells the screens of this tab. */
export function selectWorkspace(id: string): void {
  try {
    localStorage.setItem(KEY, id)
  } catch {
    // Without storage the choice lasts only until the next load.
  }
  notify()
}

/** Forgets the choice, as a sign-out does. */
export function forgetSelectedWorkspace(): void {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // Nothing to forget.
  }
  notify()
}

/** For `useSyncExternalStore`: this tab's changes and, through `storage`, the other tabs'. */
export function subscribeToWorkspaceChoice(listener: () => void): () => void {
  listeners.add(listener)
  function onStorage(event: StorageEvent) {
    if (event.key === KEY || event.key === null) listener()
  }
  window.addEventListener('storage', onStorage)
  return () => {
    listeners.delete(listener)
    window.removeEventListener('storage', onStorage)
  }
}

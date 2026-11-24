/** Something the person did on this device that other modules may want to react to. */
export type LocalAction = {
  type: 'transaction-saved' | 'contribution-saved'
  workspaceId: string
  categoryId?: string
  /** `YYYY-MM` of the saved transaction. */
  month?: string
  /** True when the save left the offline queue late, so nothing should celebrate it. */
  fromQueue: boolean
}

type Listener = (action: LocalAction) => void

const listeners = new Set<Listener>()

const lastAt = new Map<string, number>()

/** When the person last did something on this device in the family; queued saves do not count. */
export function lastLocalActionAt(workspaceId: string): number | null {
  return lastAt.get(workspaceId) ?? null
}

/** Tells every listener, in the order they subscribed; one that throws does not stop the others. */
export function emitLocalAction(action: LocalAction): void {
  if (!action.fromQueue) lastAt.set(action.workspaceId, Date.now())
  for (const listener of [...listeners]) {
    try {
      listener(action)
    } catch (error) {
      console.error('A local action listener failed', error)
    }
  }
}

/** Subscribes to local actions; the returned function unsubscribes. */
export function onLocalAction(listener: Listener): () => void {
  // A wrapper, so the same function subscribed twice is two subscriptions.
  const entry: Listener = (action) => listener(action)
  listeners.add(entry)
  return () => {
    listeners.delete(entry)
  }
}

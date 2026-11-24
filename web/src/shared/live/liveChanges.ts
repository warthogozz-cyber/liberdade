/** A change another device made to the family's data, as the live stream announced it. */
export type LiveChange = { workspaceId: string; entity: string; id?: string }

type Listener = (change: LiveChange) => void

const listeners = new Set<Listener>()

/** Tells every listener, in the order they subscribed; one that throws does not stop the others. */
export function emitLiveChange(change: LiveChange): void {
  for (const listener of [...listeners]) {
    try {
      listener(change)
    } catch (error) {
      console.error('A live change listener failed', error)
    }
  }
}

/** Subscribes to live changes; the returned function unsubscribes. */
export function onLiveChange(listener: Listener): () => void {
  // A wrapper, so the same function subscribed twice is two subscriptions.
  const entry: Listener = (change) => listener(change)
  listeners.add(entry)
  return () => {
    listeners.delete(entry)
  }
}

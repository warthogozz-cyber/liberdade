const listeners = new Set<() => void>()

/** Tells the listeners that a spending entry was just queued on this device. */
export function notifyQueued(): void {
  for (const listener of [...listeners]) {
    try {
      listener()
    } catch (error) {
      console.error('A queued listener failed', error)
    }
  }
}

/** Subscribes to queued entries; the returned function unsubscribes. */
export function onQueued(listener: () => void): () => void {
  const entry = () => listener()
  listeners.add(entry)
  return () => {
    listeners.delete(entry)
  }
}

/** How long this device remembers that it saved a transaction itself. */
const LOCAL_MS = 5 * 60_000
/** How long a transaction another device saved counts as a recent arrival. */
const ARRIVAL_MS = 60_000

const locals = new Map<string, number>()
const arrivals = new Map<string, number>()
const arrivalListeners = new Set<(id: string) => void>()

function prune(map: Map<string, number>, ttl: number, now: number): void {
  for (const [id, at] of map) if (now - at >= ttl) map.delete(id)
}

/** Remembers that this device saved the transaction, so the stream's notice of it is no arrival. */
export function markLocalChange(id: string, now = Date.now()): void {
  prune(locals, LOCAL_MS, now)
  locals.set(id, now)
}

/** Whether this device saved the transaction in the last five minutes. */
export function isLocalChange(id: string, now = Date.now()): boolean {
  const at = locals.get(id)
  return at !== undefined && now - at < LOCAL_MS
}

/** Remembers that the transaction arrived from another device, and tells the arrival listeners. */
export function markRemoteArrival(id: string, now = Date.now()): void {
  prune(arrivals, ARRIVAL_MS, now)
  arrivals.set(id, now)
  for (const listener of [...arrivalListeners]) {
    try {
      listener(id)
    } catch (error) {
      console.error('An arrival listener failed', error)
    }
  }
}

/** Whether the transaction arrived from another device in the last 60 seconds. */
export function isRecentArrival(id: string, now = Date.now()): boolean {
  const at = arrivals.get(id)
  return at !== undefined && now - at < ARRIVAL_MS
}

/** Subscribes to arrivals; the returned function unsubscribes. */
export function onArrival(listener: (id: string) => void): () => void {
  const entry = (id: string) => listener(id)
  arrivalListeners.add(entry)
  return () => {
    arrivalListeners.delete(entry)
  }
}

/** Forgets everything; for tests. */
export function resetLocalChanges(): void {
  locals.clear()
  arrivals.clear()
  arrivalListeners.clear()
}

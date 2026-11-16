const LOCK_NAME = 'liberdade-outbox'

/** Requests holding or waiting for the in-memory lock. */
let claims = 0
/** Settles when the last request in line lets go. */
let tail: Promise<unknown> = Promise.resolve()

/**
 * Runs `callback` holding the outbox lock, the way `navigator.locks.request` does: it waits its
 * turn, or with `ifAvailable` it runs at once with `held` false when someone else has the lock.
 * Web Locks keep the tabs apart; without them, an in-memory lock keeps this tab's callers apart.
 */
export async function withOutboxLock<T>(
  options: { ifAvailable?: boolean },
  callback: (held: boolean) => Promise<T>,
): Promise<T> {
  if ('locks' in navigator) {
    return navigator.locks.request(LOCK_NAME, options, (lock) => callback(lock !== null))
  }
  if (options.ifAvailable && claims > 0) return callback(false)
  claims += 1
  const release = () => {
    claims -= 1
  }
  const result = tail.then(() => callback(true))
  tail = result.then(release, release)
  return result
}

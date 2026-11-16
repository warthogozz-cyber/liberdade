import { ApiError, type TransactionView } from '@/shared/api'
import { db, type OutboxItem } from './db'
import { withOutboxLock } from './lock'
import { OUTBOX_EVENT, userItems } from './outbox'

export interface SyncDeps {
  userId: string
  /** Sends the item, giving up when `signal` aborts: the sync stopping, or `SEND_TIMEOUT_MS` passing. */
  send: (item: OutboxItem, signal: AbortSignal) => Promise<TransactionView>
  onSent: (item: OutboxItem, view: TransactionView) => void
  now?: () => number
  /** Once aborted, the sync sends nothing more and answers `done`. */
  signal?: AbortSignal
}

export interface SyncResult {
  status: 'done' | 'waiting' | 'unauthorized' | 'busy'
  /** When the first item in line is due, for `waiting` with something queued. */
  nextAt?: number
}

const BUSY_RETRY_MS = 1000

/** How long `startOutboxSync` waits to try again after a run fails outright. */
export const FAILED_RUN_RETRY_MS = 5000

/** How long a send may go unanswered before the sync gives up on it and tries again later. */
export const SEND_TIMEOUT_MS = 15_000

/** The wait before the next try: doubling from 2 s up to 5 min, spread by ±20%. */
export function backoffMs(attempts: number, random: () => number = Math.random): number {
  const base = Math.min(2000 * 2 ** (attempts - 1), 300_000)
  return Math.round(base * (0.8 + 0.4 * random()))
}

/** A failure worth trying again: no answer, a timeout, rate limiting, the server, a stale token. */
function isTransient(error: ApiError): boolean {
  return (
    error.status === 0 ||
    error.status === 408 ||
    error.status === 429 ||
    error.status >= 500 ||
    (error.status === 403 && error.code === 'csrf.invalid')
  )
}

/**
 * Records a failed send. Returns the result that stops the run, or `undefined` to go on with the
 * next item. Anything that is not an ApiError counts as a network failure.
 */
async function recordFailure(
  item: OutboxItem,
  error: unknown,
  now: () => number,
): Promise<SyncResult | undefined> {
  if (error instanceof ApiError && error.status === 401) return { status: 'unauthorized' }
  if (error instanceof ApiError && !isTransient(error)) {
    await db.outbox.update(item.id, {
      status: 'needs-attention',
      problem: { code: error.code, detail: error.detail },
    })
    return undefined
  }
  const attempts = item.attempts + 1
  const sendAfter = now() + backoffMs(attempts)
  await db.outbox.update(item.id, { attempts, sendAfter })
  return { status: 'waiting', nextAt: sendAfter }
}

/** Rejects with the signal's reason once it aborts. */
function untilAborted(signal: AbortSignal): Promise<never> {
  return new Promise((_, reject) => {
    signal.addEventListener('abort', () => reject(signal.reason as Error), { once: true })
  })
}

/**
 * Sends the first of the user's pending items, if it is due. Returns the result that stops the run,
 * or `undefined` to go on with the next item. The caller holds the lock.
 *
 * A send unanswered after `SEND_TIMEOUT_MS` counts as a network failure, so the lock is let go and
 * the item waits for its backoff; the run also stops waiting for it when a stall sits outside the
 * request itself (the CSRF token). A send cut short by the sync stopping still counts as an attempt,
 * since the request may have reached the server, and the run then stops.
 */
async function sendFirst({
  userId,
  send,
  onSent,
  now = Date.now,
  signal,
}: SyncDeps): Promise<SyncResult | undefined> {
  const item = await userItems(userId)
    .filter((queued) => queued.status === 'pending')
    .first()
  if (!item) return { status: 'done' }
  if (item.sendAfter > now()) return { status: 'waiting', nextAt: item.sendAfter }
  // Looked at again here, since the sync may have been stopped while the item was read.
  if (signal?.aborted) return { status: 'done' }
  const timeout = AbortSignal.timeout(SEND_TIMEOUT_MS)
  const sendSignal = signal ? AbortSignal.any([signal, timeout]) : timeout
  let view: TransactionView
  try {
    view = await Promise.race([send(item, sendSignal), untilAborted(timeout)])
  } catch (error) {
    const result = await recordFailure(item, error, now)
    return signal?.aborted ? { status: 'done' } : result
  }
  await db.outbox.delete(item.id)
  onSent(item, view)
  return undefined
}

/**
 * Sends the user's pending items, one at a time and in order. It takes the lock for each item and
 * lets go between them, so a cancel that waits for the lock gets in before the next send (D15, D34).
 * Only one sync sends at a time across tabs: if the lock is held when it comes for an item, it
 * answers `busy`. Only the user's own items are read, so someone else's queue waits for them (RF2).
 * It does not fire `liberdade:outbox` (T9-no-self-wake).
 */
export async function syncOutbox(deps: SyncDeps): Promise<SyncResult> {
  for (;;) {
    if (deps.signal?.aborted) return { status: 'done' }
    if (!navigator.onLine) return { status: 'waiting' }
    const result = await withOutboxLock({ ifAvailable: true }, (held) =>
      held ? sendFirst(deps) : Promise.resolve<SyncResult>({ status: 'busy' }),
    )
    if (result) return result
  }
}

/**
 * Runs `syncOutbox` now and again on `online`, `focus`, `liberdade:outbox`, the page becoming
 * visible, the first item's `nextAt`, 1 s after a `busy`, and 5 s after a run that fails outright.
 * It keeps at most one timer. Returns the function that stops it all: a run already in flight then
 * sends nothing more and schedules nothing.
 */
export function startOutboxSync(
  deps: Omit<SyncDeps, 'signal'> & { onUnauthorized: () => void },
): () => void {
  const now = deps.now ?? Date.now
  const controller = new AbortController()
  const { signal } = controller
  let timer: ReturnType<typeof setTimeout> | undefined

  function schedule(delay: number): void {
    clearTimeout(timer)
    timer = setTimeout(run, delay)
  }

  function settle(result: SyncResult): void {
    if (signal.aborted) return
    clearTimeout(timer)
    timer = undefined
    if (result.status === 'busy') schedule(BUSY_RETRY_MS)
    else if (result.status === 'waiting' && result.nextAt !== undefined) {
      schedule(Math.max(0, result.nextAt - now()))
    } else if (result.status === 'unauthorized') deps.onUnauthorized()
  }

  function retryLater(): void {
    if (!signal.aborted) schedule(FAILED_RUN_RETRY_MS)
  }

  function run(): void {
    syncOutbox({ ...deps, signal })
      .then(settle)
      .catch(retryLater)
  }

  function runIfVisible(): void {
    if (document.visibilityState === 'visible') run()
  }

  window.addEventListener('online', run)
  window.addEventListener('focus', run)
  window.addEventListener(OUTBOX_EVENT, run)
  document.addEventListener('visibilitychange', runIfVisible)
  run()

  return () => {
    controller.abort()
    clearTimeout(timer)
    window.removeEventListener('online', run)
    window.removeEventListener('focus', run)
    window.removeEventListener(OUTBOX_EVENT, run)
    document.removeEventListener('visibilitychange', runIfVisible)
  }
}

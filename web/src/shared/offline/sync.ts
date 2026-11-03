import type { QueryClient } from '@tanstack/react-query'
import {
  ApiError,
  queryKeys,
  type ApiMoney,
  type DashboardView,
  type TransactionView,
} from '@/shared/api'
import { centsToApi, parseCents } from '@/shared/money'
import { db, type OutboxItem } from './db'
import { withOutboxLock } from './lock'
import { OUTBOX_EVENT, userItems } from './outbox'

export interface SyncDeps {
  userId: string
  send: (item: OutboxItem) => Promise<TransactionView>
  onSent: (item: OutboxItem, view: TransactionView) => void
  now?: () => number
}

export interface SyncResult {
  status: 'done' | 'waiting' | 'unauthorized' | 'busy'
  /** When the first item in line is due, for `waiting` with something queued. */
  nextAt?: number
}

const BUSY_RETRY_MS = 1000

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

/** Sends the user's due items in order, holding the lock, until one is not due or fails. */
async function sendDue({ userId, send, onSent, now = Date.now }: SyncDeps): Promise<SyncResult> {
  for (;;) {
    const item = await userItems(userId)
      .filter((queued) => queued.status === 'pending')
      .first()
    if (!item) return { status: 'done' }
    if (item.sendAfter > now()) return { status: 'waiting', nextAt: item.sendAfter }
    let view: TransactionView
    try {
      view = await send(item)
    } catch (error) {
      const stop = await recordFailure(item, error, now)
      if (stop) return stop
      continue
    }
    await db.outbox.delete(item.id)
    onSent(item, view)
  }
}

/**
 * Sends the user's pending items, one at a time and in order. Only one sync runs at a time across
 * tabs: if another holds the lock, this one answers `busy`. Only the user's own items are read, so
 * someone else's queue waits for them (RF2). It does not fire `liberdade:outbox` (T9-no-self-wake).
 */
export function syncOutbox(deps: SyncDeps): Promise<SyncResult> {
  if (!navigator.onLine) return Promise.resolve({ status: 'waiting' })
  return withOutboxLock({ ifAvailable: true }, (held) =>
    held ? sendDue(deps) : Promise.resolve<SyncResult>({ status: 'busy' }),
  )
}

/**
 * Runs `syncOutbox` now and again on `online`, `focus`, `liberdade:outbox`, the page becoming
 * visible, the first item's `nextAt`, and 1 s after a `busy`. It keeps at most one timer. Returns the
 * function that stops it all; a run already in flight then schedules nothing.
 */
export function startOutboxSync(deps: SyncDeps & { onUnauthorized: () => void }): () => void {
  const now = deps.now ?? Date.now
  let timer: ReturnType<typeof setTimeout> | undefined
  let stopped = false

  function settle(result: SyncResult): void {
    if (stopped) return
    clearTimeout(timer)
    timer = undefined
    if (result.status === 'busy') timer = setTimeout(run, BUSY_RETRY_MS)
    else if (result.status === 'waiting' && result.nextAt !== undefined) {
      timer = setTimeout(run, Math.max(0, result.nextAt - now()))
    } else if (result.status === 'unauthorized') deps.onUnauthorized()
  }

  function run(): void {
    syncOutbox(deps).then(settle, (error: unknown) => {
      console.error('The outbox sync failed', error)
    })
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
    stopped = true
    clearTimeout(timer)
    window.removeEventListener('online', run)
    window.removeEventListener('focus', run)
    window.removeEventListener(OUTBOX_EVENT, run)
    document.removeEventListener('visibilitychange', runIfVisible)
  }
}

/** The API's order for a month's list: newest date, then newest record, then highest id. */
function newestFirst(a: TransactionView, b: TransactionView): number {
  return (
    b.occurredOn.localeCompare(a.occurredOn) ||
    b.createdAt.localeCompare(a.createdAt) ||
    b.id.localeCompare(a.id)
  )
}

/** The list with `view` where the API would put it; unchanged if a refetch already brought it. */
function withRow(list: TransactionView[], view: TransactionView): TransactionView[] {
  if (list.some((row) => row.id === view.id)) return list
  const index = list.findIndex((row) => newestFirst(view, row) < 0)
  return index === -1 ? [...list, view] : [...list.slice(0, index), view, ...list.slice(index)]
}

function shift(money: ApiMoney, cents: bigint): ApiMoney {
  return { ...money, amount: centsToApi(parseCents(money.amount) + cents) }
}

/**
 * Puts a sent transaction into the cache before the refetch, so the home screen does not flicker:
 * an expense adds to its month's expenses and leaves every cached balance, an income adds to every
 * cached balance, and either joins its month's list when that list is cached. Nothing new is cached.
 * Then the family's queries are refetched.
 */
export function applySentTransaction(
  queryClient: QueryClient,
  workspaceId: string,
  view: TransactionView,
): void {
  if (view.kind === 'EXPENSE' || view.kind === 'INCOME') {
    const month = view.occurredOn.slice(0, 7)
    const cents = parseCents(view.amount.amount)
    queryClient.setQueriesData<DashboardView>(
      { queryKey: [...queryKeys.workspace(workspaceId), 'dashboard'] },
      (dashboard) =>
        dashboard && {
          ...dashboard,
          balance: shift(dashboard.balance, view.kind === 'EXPENSE' ? -cents : cents),
        },
    )
    if (view.kind === 'EXPENSE') {
      queryClient.setQueryData<DashboardView>(
        queryKeys.dashboard(workspaceId, month),
        (dashboard) => dashboard && { ...dashboard, expenses: shift(dashboard.expenses, cents) },
      )
    }
    queryClient.setQueryData<TransactionView[]>(
      queryKeys.transactions(workspaceId, month),
      (list) => list && withRow(list, view),
    )
  }
  void queryClient.invalidateQueries({ queryKey: queryKeys.workspace(workspaceId) })
}

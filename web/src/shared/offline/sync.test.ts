import { afterEach, describe, expect, it, vi, type Mock } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import {
  ApiError,
  postTransaction,
  queryKeys,
  type CreateTransactionRequest,
  type DashboardView,
  type TransactionView,
} from '@/shared/api'
import {
  SEND_DELAY_MS,
  applySentTransaction,
  backoffMs,
  cancelOutboxItem,
  db,
  enqueueTransaction,
  startOutboxSync,
  syncOutbox,
  type OutboxItem,
  type SyncDeps,
} from '@/shared/offline'
import { createTestQueryClient } from '@/shared/testing'

const NOW = 1_793_700_000_000

function payload(id: string, overrides: Partial<CreateTransactionRequest> = {}) {
  return {
    id,
    kind: 'EXPENSE',
    amount: { amount: '12.34', currency: 'BRL' },
    occurredOn: '2026-11-03',
    accountId: 'account',
    categoryId: 'category',
    ...overrides,
  } satisfies CreateTransactionRequest
}

/** A pending item of `a` in `ws1`, due at `NOW`. */
function item(id: string, overrides: Partial<OutboxItem> = {}): OutboxItem {
  return {
    id,
    userId: 'a',
    workspaceId: 'ws1',
    type: 'transaction.create',
    payload: payload(id),
    createdAt: NOW - SEND_DELAY_MS,
    sendAfter: NOW,
    attempts: 0,
    status: 'pending',
    ...overrides,
  }
}

function viewOf(request: CreateTransactionRequest, createdAt = '2026-11-03T12:00:00Z') {
  return {
    ...request,
    description: request.description ?? null,
    createdAt,
    memberId: 'member',
  } satisfies TransactionView
}

interface TestDeps extends SyncDeps {
  send: Mock<SyncDeps['send']>
  onSent: Mock<SyncDeps['onSent']>
}

/** Sends succeed with the view the API would give; the clock stands at `NOW`. */
function syncDeps(overrides: Partial<TestDeps> = {}): TestDeps {
  return {
    userId: 'a',
    send: vi.fn<SyncDeps['send']>((queued) => Promise.resolve(viewOf(queued.payload))),
    onSent: vi.fn<SyncDeps['onSent']>(),
    now: () => NOW,
    ...overrides,
  }
}

function sentIds(send: Mock<SyncDeps['send']>) {
  return send.mock.calls.map(([queued]) => queued.id)
}

/** A promise the test resolves by hand. */
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

/**
 * Waits on the real clock until `check` holds. `vi.waitFor` would move the fake clock while it
 * polls; `setInterval` is not faked here, so this leaves the fake one where the test put it.
 */
function until(check: () => boolean): Promise<void> {
  return new Promise((resolve, reject) => {
    let tries = 0
    const timer = setInterval(() => {
      if (check()) {
        clearInterval(timer)
        resolve()
      } else if (++tries > 400) {
        clearInterval(timer)
        reject(new Error('until: the condition never held'))
      }
    }, 5)
  })
}

/** Lets in-flight IndexedDB work finish, on the real clock. */
function settle(): Promise<void> {
  let ticks = 0
  return until(() => ++ticks > 6)
}

/** A `navigator.locks` that grants one lock at a time, as the browser does across tabs. */
class FakeLockManager {
  readonly names: string[] = []
  private held = false
  private readonly queue: (() => void)[] = []

  async request(
    name: string,
    options: LockOptions,
    callback: LockGrantedCallback<unknown>,
  ): Promise<unknown> {
    this.names.push(name)
    if (this.held) {
      if (options.ifAvailable) return callback(null)
      await new Promise<void>((resolve) => this.queue.push(resolve))
    }
    this.held = true
    try {
      return await callback({ name, mode: 'exclusive' })
    } finally {
      const next = this.queue.shift()
      if (next) next()
      else this.held = false
    }
  }
}

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('syncOutbox', () => {
  it('sends the due items one at a time, in the order they were made', async () => {
    await db.outbox.bulkAdd([item('t1', { createdAt: 200 }), item('t2', { createdAt: 100 })])
    const deps = syncDeps()

    expect(await syncOutbox(deps)).toEqual({ status: 'done' })

    expect(sentIds(deps.send)).toEqual(['t2', 't1'])
    expect(await db.outbox.count()).toBe(0)
    expect(deps.onSent).toHaveBeenCalledTimes(2)
    expect(deps.onSent).toHaveBeenNthCalledWith(
      1,
      item('t2', { createdAt: 100 }),
      viewOf(payload('t2')),
    )
  })

  it('waits for an item that is not due yet', async () => {
    await db.outbox.add(item('t1', { sendAfter: NOW + 1 }))
    const deps = syncDeps()

    expect(await syncOutbox(deps)).toEqual({ status: 'waiting', nextAt: NOW + 1 })

    expect(deps.send).not.toHaveBeenCalled()
  })

  it('lets no one pass an item that is waiting out a backoff', async () => {
    await db.outbox.bulkAdd([
      item('t1', { createdAt: 100, sendAfter: NOW + 5000, attempts: 2 }),
      item('t2', { createdAt: 200, sendAfter: NOW - 1 }),
    ])
    const deps = syncDeps()

    expect(await syncOutbox(deps)).toEqual({ status: 'waiting', nextAt: NOW + 5000 })

    expect(deps.send).not.toHaveBeenCalled()
  })

  it('skips the items that need attention', async () => {
    await db.outbox.bulkAdd([
      item('t1', { createdAt: 100, status: 'needs-attention', problem: { code: 'x' } }),
      item('t2', { createdAt: 200 }),
    ])
    const deps = syncDeps()

    expect(await syncOutbox(deps)).toEqual({ status: 'done' })

    expect(sentIds(deps.send)).toEqual(['t2'])
    expect(await db.outbox.get('t1')).toEqual(
      item('t1', { createdAt: 100, status: 'needs-attention', problem: { code: 'x' } }),
    )
  })

  it.each([
    ['no answer', new ApiError(0, 'network')],
    ['a 408', new ApiError(408, 'http')],
    ['a 429', new ApiError(429, 'rate-limited')],
    ['a 503', new ApiError(503, 'http')],
    ['a 403 csrf.invalid', new ApiError(403, 'csrf.invalid', 'Sua sessão foi atualizada.')],
    ['an unknown failure', new Error('boom')],
  ])('backs off and waits after %s', async (_, error) => {
    vi.spyOn(Math, 'random').mockReturnValue(0.5)
    await db.outbox.bulkAdd([item('t1', { createdAt: 100 }), item('t2', { createdAt: 200 })])
    const deps = syncDeps({ send: vi.fn<SyncDeps['send']>().mockRejectedValue(error) })

    expect(await syncOutbox(deps)).toEqual({ status: 'waiting', nextAt: NOW + 2000 })

    expect(await db.outbox.get('t1')).toEqual(
      item('t1', { createdAt: 100, attempts: 1, sendAfter: NOW + backoffMs(1, () => 0.5) }),
    )
    expect(sentIds(deps.send)).toEqual(['t1'])
    expect(deps.onSent).not.toHaveBeenCalled()
  })

  it('doubles the wait on the next failure', async () => {
    vi.spyOn(Math, 'random').mockReturnValue(0.5)
    await db.outbox.add(item('t1', { attempts: 1 }))
    const deps = syncDeps({
      send: vi.fn<SyncDeps['send']>().mockRejectedValue(new ApiError(0, 'network')),
    })

    expect(await syncOutbox(deps)).toEqual({ status: 'waiting', nextAt: NOW + 4000 })

    expect(await db.outbox.get('t1')).toEqual(item('t1', { attempts: 2, sendAfter: NOW + 4000 }))
  })

  it.each([
    new ApiError(422, 'transaction.invalid-id', 'O identificador do lançamento é inválido.'),
    new ApiError(403, 'auth.forbidden'),
  ])('sets aside an item the API refuses with $status $code and sends the next', async (error) => {
    await db.outbox.bulkAdd([item('t1', { createdAt: 100 }), item('t2', { createdAt: 200 })])
    const send = vi
      .fn<SyncDeps['send']>((queued) => Promise.resolve(viewOf(queued.payload)))
      .mockRejectedValueOnce(error)
    const deps = syncDeps({ send })

    expect(await syncOutbox(deps)).toEqual({ status: 'done' })

    expect(await db.outbox.toArray()).toEqual([
      item('t1', {
        createdAt: 100,
        status: 'needs-attention',
        problem: { code: error.code, detail: error.detail },
      }),
    ])
    expect(sentIds(send)).toEqual(['t1', 't2'])
    expect(deps.onSent).toHaveBeenCalledTimes(1)
  })

  it('stops on a 401 and leaves the item as it was', async () => {
    await db.outbox.bulkAdd([item('t1', { createdAt: 100 }), item('t2', { createdAt: 200 })])
    const deps = syncDeps({
      send: vi.fn<SyncDeps['send']>().mockRejectedValue(new ApiError(401, 'auth.required')),
    })

    expect(await syncOutbox(deps)).toEqual({ status: 'unauthorized' })

    expect(await db.outbox.get('t1')).toEqual(item('t1', { createdAt: 100 }))
    expect(sentIds(deps.send)).toEqual(['t1'])
  })

  it('does not try while offline', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    await db.outbox.add(item('t1'))
    const deps = syncDeps()

    expect(await syncOutbox(deps)).toEqual({ status: 'waiting' })

    expect(deps.send).not.toHaveBeenCalled()
  })

  it('never sends another person’s items', async () => {
    await db.outbox.bulkAdd([item('t1'), item('t2', { createdAt: NOW })])
    const deps = syncDeps({ userId: 'b' })

    expect(await syncOutbox(deps)).toEqual({ status: 'done' })

    expect(deps.send).not.toHaveBeenCalled()
    expect(await db.outbox.count()).toBe(2)
  })

  it('does not announce its own changes', async () => {
    await db.outbox.add(item('t1'))
    const listener = vi.fn()
    window.addEventListener('liberdade:outbox', listener)

    await syncOutbox(syncDeps())

    window.removeEventListener('liberdade:outbox', listener)
    expect(listener).not.toHaveBeenCalled()
  })

  it('sends through the API and hands the stored transaction to onSent', async () => {
    const workspace = seedMockFamily()
    const id = '0192f3a4-1111-7000-8000-000000000001'
    const queued = await enqueueTransaction(
      {
        userId: mockState.user.id,
        workspaceId: workspace.id,
        payload: payload(id, {
          accountId: mockState.accounts[0].id,
          categoryId: mockState.categories[0].id,
        }),
      },
      Date.now() - SEND_DELAY_MS,
    )
    const onSent = vi.fn()

    const result = await syncOutbox({
      userId: mockState.user.id,
      send: (sending) => postTransaction(sending.workspaceId, sending.payload),
      onSent,
    })

    expect(result).toEqual({ status: 'done' })
    expect(mockState.transactions).toHaveLength(1)
    expect(mockState.transactions[0]).toMatchObject({ id, amount: { amount: '12.34' } })
    expect(onSent).toHaveBeenCalledWith(queued, mockState.transactions[0])
    expect(await db.outbox.count()).toBe(0)
  })
})

describe.each([
  ['with Web Locks', true],
  ['without Web Locks', false],
])('the outbox lock, %s', (_, webLocks) => {
  let manager: FakeLockManager | undefined

  function installLocks() {
    if (!webLocks) return
    manager = new FakeLockManager()
    Object.defineProperty(navigator, 'locks', { value: manager, configurable: true })
  }

  afterEach(() => {
    if (manager) {
      Reflect.deleteProperty(navigator, 'locks')
      manager = undefined
    }
  })

  function expectLockUsed() {
    if (webLocks) {
      expect(manager?.names.length).toBeGreaterThan(0)
      expect(new Set(manager?.names)).toEqual(new Set(['liberdade-outbox']))
    } else {
      expect('locks' in navigator).toBe(false)
    }
  }

  it('lets one sync run at a time, so each item is sent once and in order', async () => {
    installLocks()
    await db.outbox.bulkAdd([item('t1', { createdAt: 200 }), item('t2', { createdAt: 100 })])
    const deps = syncDeps()

    const results = await Promise.all([syncOutbox(deps), syncOutbox(deps)])

    expect(results.map((result) => result.status).sort()).toEqual(['busy', 'done'])
    expect(sentIds(deps.send)).toEqual(['t2', 't1'])
    expect(deps.onSent).toHaveBeenCalledTimes(2)
    expectLockUsed()
  })

  it('makes cancel wait for a send in flight (D34)', async () => {
    installLocks()
    await db.outbox.add(item('t1'))
    const answer = deferred<TransactionView>()
    const deps = syncDeps({ send: vi.fn<SyncDeps['send']>(() => answer.promise) })
    const syncing = syncOutbox(deps)
    await until(() => deps.send.mock.calls.length === 1)

    let cancelled: boolean | undefined
    const cancelling = cancelOutboxItem('t1').then((result) => (cancelled = result))
    await settle()
    expect(cancelled).toBeUndefined()

    answer.resolve(viewOf(payload('t1')))

    expect(await cancelling).toBe(false)
    expect(await syncing).toEqual({ status: 'done' })
    expect(deps.send).toHaveBeenCalledTimes(1)
    expect(deps.onSent).toHaveBeenCalledWith(item('t1'), viewOf(payload('t1')))
    expectLockUsed()
  })
})

describe('backoffMs', () => {
  it.each([
    [1, 0.5, 2000],
    [2, 0.5, 4000],
    [20, 0.5, 300000],
    [1, 0, 1600],
    [1, 1, 2400],
  ])('after %i attempts, with random %d, is %i ms', (attempts, random, expected) => {
    expect(backoffMs(attempts, () => random)).toBe(expected)
  })

  it('uses Math.random by default', () => {
    vi.spyOn(Math, 'random').mockReturnValue(0)

    expect(backoffMs(3)).toBe(6400)
  })
})

describe('startOutboxSync', () => {
  function startDeps(overrides: Partial<TestDeps> = {}) {
    return { ...syncDeps({ now: undefined, ...overrides }), onUnauthorized: vi.fn() }
  }

  it('sends an item when its delay is over, not a millisecond before', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    vi.setSystemTime(NOW)
    await enqueueTransaction({ userId: 'a', workspaceId: 'ws1', payload: payload('t1') })
    const deps = startDeps()

    const stop = startOutboxSync(deps)
    await until(() => vi.getTimerCount() === 1)
    await vi.advanceTimersByTimeAsync(SEND_DELAY_MS - 1)
    await settle()
    expect(deps.send).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    await until(() => deps.onSent.mock.calls.length === 1)

    expect(Date.now()).toBe(NOW + SEND_DELAY_MS)
    expect(sentIds(deps.send)).toEqual(['t1'])
    expect(vi.getTimerCount()).toBe(0)
    stop()
  })

  it('sends when the connection comes back', async () => {
    const onLine = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    await db.outbox.add(item('t1', { sendAfter: 0 }))
    const deps = startDeps()
    const stop = startOutboxSync(deps)
    await settle()
    expect(deps.send).not.toHaveBeenCalled()

    onLine.mockReturnValue(true)
    window.dispatchEvent(new Event('online'))

    await until(() => deps.onSent.mock.calls.length === 1)
    expect(sentIds(deps.send)).toEqual(['t1'])
    stop()
  })

  it('runs again on focus, on a new item, and when the page is shown', async () => {
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
    const deps = startDeps()
    const stop = startOutboxSync(deps)
    await settle()

    await db.outbox.add(item('t1', { sendAfter: 0 }))
    window.dispatchEvent(new Event('focus'))
    await until(() => deps.onSent.mock.calls.length === 1)

    await enqueueTransaction(
      { userId: 'a', workspaceId: 'ws1', payload: payload('t2') },
      Date.now() - SEND_DELAY_MS,
    )
    await until(() => deps.onSent.mock.calls.length === 2)

    await db.outbox.add(item('t3', { sendAfter: 0 }))
    document.dispatchEvent(new Event('visibilitychange'))
    await settle()
    expect(deps.onSent).toHaveBeenCalledTimes(2)

    visibility.mockReturnValue('visible')
    document.dispatchEvent(new Event('visibilitychange'))
    await until(() => deps.onSent.mock.calls.length === 3)

    expect(sentIds(deps.send)).toEqual(['t1', 't2', 't3'])
    stop()
  })

  it('tries again 1 s after another tab held the lock', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    vi.setSystemTime(NOW)
    await db.outbox.bulkAdd([item('t1'), item('b1', { userId: 'b' })])
    const other = deferred<TransactionView>()
    const otherSend = vi.fn<SyncDeps['send']>(() => other.promise)
    const otherTab = syncOutbox(syncDeps({ userId: 'b', send: otherSend }))
    await until(() => otherSend.mock.calls.length === 1)
    const deps = startDeps()

    const stop = startOutboxSync(deps)
    await until(() => vi.getTimerCount() === 1)
    other.resolve(viewOf(payload('b1')))
    await otherTab
    await vi.advanceTimersByTimeAsync(999)
    await settle()
    expect(deps.send).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    await until(() => deps.onSent.mock.calls.length === 1)

    expect(sentIds(deps.send)).toEqual(['t1'])
    stop()
  })

  it('reports a 401 once and then waits for the next event', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    vi.setSystemTime(NOW)
    await db.outbox.add(item('t1'))
    const deps = startDeps({
      send: vi.fn<SyncDeps['send']>().mockRejectedValue(new ApiError(401, 'auth.required')),
    })

    const stop = startOutboxSync(deps)
    await until(() => deps.onUnauthorized.mock.calls.length === 1)

    expect(vi.getTimerCount()).toBe(0)
    expect(await db.outbox.get('t1')).toEqual(item('t1'))
    stop()
  })

  it('stops listening, and a run already in flight schedules nothing', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    vi.setSystemTime(NOW)
    await db.outbox.bulkAdd([
      item('t1', { createdAt: 100 }),
      item('t2', { createdAt: 200, sendAfter: NOW + 60_000 }),
    ])
    const answer = deferred<TransactionView>()
    const deps = startDeps({ send: vi.fn<SyncDeps['send']>(() => answer.promise) })
    const stop = startOutboxSync(deps)
    await until(() => deps.send.mock.calls.length === 1)

    stop()
    answer.resolve(viewOf(payload('t1')))
    await until(() => deps.onSent.mock.calls.length === 1)
    await settle()
    expect(vi.getTimerCount()).toBe(0)

    window.dispatchEvent(new Event('online'))
    window.dispatchEvent(new Event('focus'))
    window.dispatchEvent(new Event('liberdade:outbox'))
    await vi.advanceTimersByTimeAsync(60_000)
    await settle()
    expect(deps.send).toHaveBeenCalledTimes(1)
  })

  it('keeps a single timer when several events wake it', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    vi.setSystemTime(NOW)
    await db.outbox.add(item('t1', { sendAfter: NOW + 60_000 }))
    const deps = startDeps()
    const stop = startOutboxSync(deps)
    await until(() => vi.getTimerCount() === 1)

    window.dispatchEvent(new Event('focus'))
    window.dispatchEvent(new Event('online'))
    await settle()

    expect(vi.getTimerCount()).toBe(1)
    stop()
    expect(vi.getTimerCount()).toBe(0)
  })

  it('logs a run that fails', async () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    await db.outbox.add(item('t1', { sendAfter: 0 }))
    const deps = startDeps({
      onSent: vi.fn<SyncDeps['onSent']>(() => {
        throw new Error('render failed')
      }),
    })

    const stop = startOutboxSync(deps)
    await until(() => error.mock.calls.length === 1)

    expect(await db.outbox.count()).toBe(0)
    stop()
  })
})

describe('applySentTransaction', () => {
  const ws = 'ws1'

  function dashboard(month: string, expenses: string, balance: string): DashboardView {
    return {
      month,
      expenses: { amount: expenses, currency: 'BRL' },
      balance: { amount: balance, currency: 'BRL' },
    }
  }

  function row(id: string, occurredOn: string, createdAt = '2026-11-03T12:00:00Z') {
    return viewOf(payload(id, { occurredOn }), createdAt)
  }

  it('adds an expense to its month, takes it from the balance and lists it', () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(
      queryKeys.dashboard(ws, '2026-11'),
      dashboard('2026-11', '10.00', '90.00'),
    )
    queryClient.setQueryData(queryKeys.transactions(ws, '2026-11'), [
      row('newer', '2026-11-05'),
      row('same-day-later', '2026-11-03', '2026-11-03T13:00:00Z'),
      row('same-day-earlier', '2026-11-03', '2026-11-03T11:00:00Z'),
      row('older', '2026-11-01'),
    ])

    applySentTransaction(queryClient, ws, row('sent', '2026-11-03'))

    expect(queryClient.getQueryData(queryKeys.dashboard(ws, '2026-11'))).toEqual(
      dashboard('2026-11', '22.34', '77.66'),
    )
    expect(
      queryClient
        .getQueryData<TransactionView[]>(queryKeys.transactions(ws, '2026-11'))
        ?.map((listed) => listed.id),
    ).toEqual(['newer', 'same-day-later', 'sent', 'same-day-earlier', 'older'])
    expect(queryClient.getQueryState(queryKeys.dashboard(ws, '2026-11'))?.isInvalidated).toBe(true)
    expect(queryClient.getQueryState(queryKeys.transactions(ws, '2026-11'))?.isInvalidated).toBe(
      true,
    )
  })

  it('takes an expense of another month only from the balance, and caches nothing new', () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(
      queryKeys.dashboard(ws, '2026-11'),
      dashboard('2026-11', '10.00', '5.00'),
    )

    applySentTransaction(queryClient, ws, row('sent', '2026-10-31'))

    expect(queryClient.getQueryData(queryKeys.dashboard(ws, '2026-11'))).toEqual(
      dashboard('2026-11', '10.00', '-7.34'),
    )
    expect(queryClient.getQueryCache().getAll()).toHaveLength(1)
  })

  it('adds an income to every balance and lists it at the end of an older month', () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(
      queryKeys.dashboard(ws, '2026-10'),
      dashboard('2026-10', '3.00', '90.00'),
    )
    queryClient.setQueryData(
      queryKeys.dashboard(ws, '2026-11'),
      dashboard('2026-11', '10.00', '90.00'),
    )
    queryClient.setQueryData(queryKeys.transactions(ws, '2026-10'), [row('newer', '2026-10-31')])

    applySentTransaction(queryClient, ws, {
      ...row('sent', '2026-10-01'),
      kind: 'INCOME',
    })

    expect(queryClient.getQueryData(queryKeys.dashboard(ws, '2026-10'))).toEqual(
      dashboard('2026-10', '3.00', '102.34'),
    )
    expect(queryClient.getQueryData(queryKeys.dashboard(ws, '2026-11'))).toEqual(
      dashboard('2026-11', '10.00', '102.34'),
    )
    expect(
      queryClient
        .getQueryData<TransactionView[]>(queryKeys.transactions(ws, '2026-10'))
        ?.map((listed) => listed.id),
    ).toEqual(['newer', 'sent'])
  })

  it('leaves a list that already has the transaction alone', () => {
    const queryClient = createTestQueryClient()
    const list = [row('sent', '2026-11-03')]
    queryClient.setQueryData(queryKeys.transactions(ws, '2026-11'), list)

    applySentTransaction(queryClient, ws, row('sent', '2026-11-03'))

    expect(queryClient.getQueryData(queryKeys.transactions(ws, '2026-11'))).toEqual(list)
  })

  it('only refetches for any other kind', () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(
      queryKeys.dashboard(ws, '2026-11'),
      dashboard('2026-11', '10.00', '90.00'),
    )
    queryClient.setQueryData(queryKeys.transactions(ws, '2026-11'), [])

    applySentTransaction(queryClient, ws, { ...row('sent', '2026-11-03'), kind: 'TRANSFER' })

    expect(queryClient.getQueryData(queryKeys.dashboard(ws, '2026-11'))).toEqual(
      dashboard('2026-11', '10.00', '90.00'),
    )
    expect(queryClient.getQueryData(queryKeys.transactions(ws, '2026-11'))).toEqual([])
    expect(queryClient.getQueryState(queryKeys.dashboard(ws, '2026-11'))?.isInvalidated).toBe(true)
  })

  it('leaves other families alone', () => {
    const queryClient = createTestQueryClient()
    queryClient.setQueryData(
      queryKeys.dashboard('ws2', '2026-11'),
      dashboard('2026-11', '10.00', '90.00'),
    )

    applySentTransaction(queryClient, ws, row('sent', '2026-11-03'))

    expect(queryClient.getQueryData(queryKeys.dashboard('ws2', '2026-11'))).toEqual(
      dashboard('2026-11', '10.00', '90.00'),
    )
    expect(queryClient.getQueryState(queryKeys.dashboard('ws2', '2026-11'))?.isInvalidated).toBe(
      false,
    )
  })
})

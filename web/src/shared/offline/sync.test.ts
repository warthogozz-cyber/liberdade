import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { ApiError, postTransaction, type TransactionView } from '@/shared/api'
import {
  FAILED_RUN_RETRY_MS,
  SEND_DELAY_MS,
  SEND_TIMEOUT_MS,
  backoffMs,
  cancelOutboxItem,
  enqueueTransaction,
  startOutboxSync,
  syncOutbox,
  type OutboxItem,
  type SyncDeps,
  type SyncResult,
} from '@/shared/offline'
import { db } from '@/shared/offline/db'
import { isLocalChange, markLocalChange, resetLocalChanges } from '@/shared/live/localChanges'
import { outboxItem, outboxPayload as payload, transactionView as viewOf } from '@/shared/testing'
import { withOutboxLock } from './lock'

const NOW = 1_793_700_000_000

/** A pending item of `a` in `ws1`, due at `NOW`. */
function item(id: string, overrides: Partial<OutboxItem> = {}): OutboxItem {
  return outboxItem(id, { createdAt: NOW - SEND_DELAY_MS, sendAfter: NOW, ...overrides })
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

/** A `send` that holds `t1` until the test lets it go, and sends anything else at once. */
function holdingFirst() {
  const first = deferred<TransactionView>()
  const send = vi.fn<SyncDeps['send']>((queued) =>
    queued.id === 't1' ? first.promise : Promise.resolve(viewOf(queued.payload)),
  )
  return { send, release: () => first.resolve(viewOf(payload('t1'))) }
}

function sentIds(send: Mock<SyncDeps['send']>) {
  return send.mock.calls.map(([queued]) => queued.id)
}

/** A request that never answers, until its signal aborts it, as fetch does. */
function stalled(signal: AbortSignal): Promise<never> {
  return new Promise((_, reject) => {
    signal.addEventListener('abort', () => reject(signal.reason as Error))
  })
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

afterEach(async () => {
  vi.useRealTimers()
  vi.restoreAllMocks()
  // Each test lets go of what it holds in a `finally`; this waits until the last run it left has
  // also let go of the in-memory lock, so the next test never finds it busy.
  await withOutboxLock({}, () => Promise.resolve())
})

describe('syncOutbox', () => {
  it('sendRefreshesLocalMark', async () => {
    resetLocalChanges()
    // Queued six minutes ago while offline: the enqueue mark has run out.
    markLocalChange('t1', Date.now() - 6 * 60_000)
    expect(isLocalChange('t1')).toBe(false)
    await db.outbox.add(item('t1'))
    const markedDuringSend: boolean[] = []
    const deps = syncDeps({
      send: vi.fn<SyncDeps['send']>((queued) => {
        markedDuringSend.push(isLocalChange(queued.id))
        return Promise.resolve(viewOf(queued.payload))
      }),
    })

    await syncOutbox(deps)

    expect(markedDuringSend).toEqual([true])
    expect(isLocalChange('t1')).toBe(true)
    resetLocalChanges()
  })

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

  it('ventureInvalidGoesToAttention', async () => {
    const detail =
      'Esse projeto não existe mais nesta família. Tire o projeto do lançamento e envie de novo.'
    const marked = payload('t1', { ventureId: '0192f3a4-0000-7000-8000-0000000000a1' })
    await db.outbox.add(item('t1', { payload: marked }))
    const deps = syncDeps({
      send: vi
        .fn<SyncDeps['send']>()
        .mockRejectedValue(new ApiError(422, 'transaction.venture-invalid', detail)),
    })

    expect(await syncOutbox(deps)).toEqual({ status: 'done' })

    expect(await db.outbox.get('t1')).toEqual(
      item('t1', {
        payload: marked,
        status: 'needs-attention',
        problem: { code: 'transaction.venture-invalid', detail },
      }),
    )
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

  it('sends nothing once its signal is aborted', async () => {
    await db.outbox.add(item('t1'))
    const deps = syncDeps({ signal: AbortSignal.abort() })

    expect(await syncOutbox(deps)).toEqual({ status: 'done' })

    expect(deps.send).not.toHaveBeenCalled()
    expect(await db.outbox.count()).toBe(1)
  })

  it('stops when its signal aborts the send in flight, counting the attempt', async () => {
    await db.outbox.bulkAdd([item('t1', { createdAt: 100 }), item('t2', { createdAt: 200 })])
    const controller = new AbortController()
    const send = vi.fn<SyncDeps['send']>((_, signal) => stalled(signal))
    const syncing = syncOutbox(syncDeps({ send, signal: controller.signal }))

    await until(() => send.mock.calls.length === 1)
    controller.abort()

    expect(await syncing).toEqual({ status: 'done' })
    expect(sentIds(send)).toEqual(['t1'])
    // The request may have reached the server, so Desfazer must refuse it from now on.
    expect(await db.outbox.get('t1')).toMatchObject({ status: 'pending', attempts: 1 })
  })

  it('sends nothing when its signal is aborted while it reads the next item', async () => {
    await db.outbox.add(item('t1'))
    const controller = new AbortController()
    // The clock is read once the item is in hand and before it is sent: abort right there.
    const now = () => {
      controller.abort()
      return NOW
    }
    const deps = syncDeps({ now, signal: controller.signal })

    expect(await syncOutbox(deps)).toEqual({ status: 'done' })

    expect(deps.send).not.toHaveBeenCalled()
    expect(await db.outbox.count()).toBe(1)
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

    try {
      await syncOutbox(syncDeps())

      expect(listener).not.toHaveBeenCalled()
    } finally {
      window.removeEventListener('liberdade:outbox', listener)
    }
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
      send: (sending, signal) => postTransaction(sending.workspaceId, sending.payload, signal),
      onSent,
    })

    expect(result).toEqual({ status: 'done' })
    expect(mockState.transactions).toHaveLength(1)
    expect(mockState.transactions[0]).toMatchObject({ id, amount: { amount: '12.34' } })
    expect(onSent).toHaveBeenCalledWith(queued, mockState.transactions[0])
    expect(await db.outbox.count()).toBe(0)
  })
  it('lets postTransaction give up when its signal aborts, sending nothing', async () => {
    const workspace = seedMockFamily()
    const request = payload('0192f3a4-1111-7000-8000-000000000002', {
      accountId: mockState.accounts[0].id,
      categoryId: mockState.categories[0].id,
    })

    await expect(postTransaction(workspace.id, request, AbortSignal.abort())).rejects.toMatchObject(
      { name: 'AbortError' },
    )

    expect(mockState.transactions).toHaveLength(0)
  })

  describe('without AbortSignal.any (iOS before 17.4)', () => {
    let any: PropertyDescriptor | undefined

    beforeEach(() => {
      any = Object.getOwnPropertyDescriptor(AbortSignal, 'any')
      Reflect.deleteProperty(AbortSignal, 'any')
    })

    afterEach(() => {
      if (any) Object.defineProperty(AbortSignal, 'any', any)
    })

    it('still sends', async () => {
      await db.outbox.add(item('t1'))
      const deps = syncDeps()

      expect(await syncOutbox({ ...deps, signal: new AbortController().signal })).toEqual({
        status: 'done',
      })

      expect(sentIds(deps.send)).toEqual(['t1'])
      expect(await db.outbox.count()).toBe(0)
    })

    it('still aborts the send in flight when the sync stops', async () => {
      await db.outbox.add(item('t1'))
      const controller = new AbortController()
      const send = vi.fn<SyncDeps['send']>((_, signal) => stalled(signal))
      const syncing = syncOutbox(syncDeps({ send, signal: controller.signal }))

      await until(() => send.mock.calls.length === 1)
      controller.abort()

      expect(await syncing).toEqual({ status: 'done' })
      expect(send.mock.calls[0][1].aborted).toBe(true)
    })

    it('still aborts the send in flight when it times out', async () => {
      await db.outbox.add(item('t1'))
      const timer = new AbortController()
      vi.spyOn(AbortSignal, 'timeout').mockReturnValue(timer.signal)
      const send = vi.fn<SyncDeps['send']>((_, signal) => stalled(signal))
      const syncing = syncOutbox(syncDeps({ send, signal: new AbortController().signal }))

      await until(() => send.mock.calls.length === 1)
      timer.abort(new DOMException('The operation timed out.', 'TimeoutError'))

      expect(await syncing).toMatchObject({ status: 'waiting' })
      expect(send.mock.calls[0][1].reason).toMatchObject({ name: 'TimeoutError' })
      expect(await db.outbox.get('t1')).toMatchObject({ status: 'pending', attempts: 1 })
    })
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
    const { send, release } = holdingFirst()
    const deps = syncDeps({ send })
    const syncing = syncOutbox(deps)

    try {
      await until(() => send.mock.calls.length === 1)
      let cancelled: boolean | undefined
      const cancelling = cancelOutboxItem('t1').then((result) => (cancelled = result))
      await settle()
      expect(cancelled).toBeUndefined()

      release()

      expect(await cancelling).toBe(false)
      // The cancel holds the lock when the sync comes back for the next item.
      expect(await syncing).toEqual({ status: 'busy' })
      expect(send).toHaveBeenCalledTimes(1)
      expect(deps.onSent).toHaveBeenCalledWith(item('t1'), viewOf(payload('t1')))
      expectLockUsed()
    } finally {
      release()
      await syncing
    }
  })

  it('lets a cancel in between two sends, so it is not stuck behind the queue (D15)', async () => {
    installLocks()
    await db.outbox.bulkAdd([item('t1', { createdAt: 100 }), item('t2', { createdAt: 200 })])
    const { send, release } = holdingFirst()
    const deps = syncDeps({ send })
    const syncing = syncOutbox(deps)

    try {
      await until(() => send.mock.calls.length === 1)
      let cancelled: boolean | undefined
      const cancelling = cancelOutboxItem('t2').then((result) => (cancelled = result))
      await settle()
      expect(cancelled).toBeUndefined()

      release()

      expect(await cancelling).toBe(true)
      expect(await syncing).toEqual({ status: 'busy' })
      expect(await syncOutbox(deps)).toEqual({ status: 'done' })
      expect(sentIds(send)).toEqual(['t1'])
      expect(await db.outbox.count()).toBe(0)
      expectLockUsed()
    } finally {
      release()
      await syncing
    }
  })

  it('announces a cancel once the lock is free, so the sync it wakes can run', async () => {
    installLocks()
    await db.outbox.add(item('t1', { sendAfter: NOW + 60_000 }))
    const woken: Promise<SyncResult>[] = []
    const wake = () => woken.push(syncOutbox(syncDeps()))
    window.addEventListener('liberdade:outbox', wake)

    try {
      expect(await cancelOutboxItem('t1')).toBe(true)

      expect(woken).toHaveLength(1)
      expect(await woken[0]).toEqual({ status: 'done' })
      expectLockUsed()
    } finally {
      window.removeEventListener('liberdade:outbox', wake)
      await Promise.all(woken)
    }
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
    return { ...syncDeps({ now: undefined, ...overrides }), onUnauthorized: vi.fn<() => void>() }
  }

  function useFakeClock() {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    vi.setSystemTime(NOW)
  }

  it('sends an item when its delay is over, not a millisecond before', async () => {
    useFakeClock()
    await enqueueTransaction({ userId: 'a', workspaceId: 'ws1', payload: payload('t1') })
    const deps = startDeps()
    const stop = startOutboxSync(deps)

    try {
      await until(() => vi.getTimerCount() === 1)
      await vi.advanceTimersByTimeAsync(SEND_DELAY_MS - 1)
      await settle()
      expect(deps.send).not.toHaveBeenCalled()

      await vi.advanceTimersByTimeAsync(1)
      await until(() => deps.onSent.mock.calls.length === 1)

      expect(Date.now()).toBe(NOW + SEND_DELAY_MS)
      expect(sentIds(deps.send)).toEqual(['t1'])
      expect(vi.getTimerCount()).toBe(0)
    } finally {
      stop()
    }
  })

  it('sends when the connection comes back', async () => {
    const onLine = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    await db.outbox.add(item('t1', { sendAfter: 0 }))
    const deps = startDeps()
    const stop = startOutboxSync(deps)

    try {
      await settle()
      expect(deps.send).not.toHaveBeenCalled()

      onLine.mockReturnValue(true)
      window.dispatchEvent(new Event('online'))

      await until(() => deps.onSent.mock.calls.length === 1)
      expect(sentIds(deps.send)).toEqual(['t1'])
    } finally {
      stop()
    }
  })

  it('runs again on focus, on a new item, and when the page is shown', async () => {
    const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
    const deps = startDeps()
    const stop = startOutboxSync(deps)

    try {
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
    } finally {
      stop()
    }
  })

  it('tries again 1 s after another tab held the lock', async () => {
    useFakeClock()
    await db.outbox.bulkAdd([item('t1'), item('b1', { userId: 'b' })])
    const other = deferred<TransactionView>()
    const otherSend = vi.fn<SyncDeps['send']>(() => other.promise)
    const otherTab = syncOutbox(syncDeps({ userId: 'b', send: otherSend }))
    const deps = startDeps()
    let stop = () => {}

    try {
      await until(() => otherSend.mock.calls.length === 1)
      stop = startOutboxSync(deps)
      await until(() => vi.getTimerCount() === 1)
      other.resolve(viewOf(payload('b1')))
      await otherTab
      await vi.advanceTimersByTimeAsync(999)
      await settle()
      expect(deps.send).not.toHaveBeenCalled()

      await vi.advanceTimersByTimeAsync(1)
      await until(() => deps.onSent.mock.calls.length === 1)

      expect(sentIds(deps.send)).toEqual(['t1'])
    } finally {
      stop()
      other.resolve(viewOf(payload('b1')))
      await otherTab
    }
  })

  it('gives up on a send stalled for 15 s, lets go of the lock and tries again (I2)', async () => {
    useFakeClock()
    // jsdom's AbortSignal.timeout runs on its own clock; this one runs on the fake one.
    const timeout = vi.spyOn(AbortSignal, 'timeout').mockImplementation((ms) => {
      const controller = new AbortController()
      setTimeout(() => {
        controller.abort(new DOMException('The operation timed out.', 'TimeoutError'))
      }, ms)
      return controller.signal
    })
    vi.spyOn(Math, 'random').mockReturnValue(0.5)
    await db.outbox.add(item('t1'))
    const signals: AbortSignal[] = []
    const send = vi.fn<SyncDeps['send']>((queued, signal) => {
      signals.push(signal)
      return signals.length === 1 ? stalled(signal) : Promise.resolve(viewOf(queued.payload))
    })
    const deps = startDeps({ send })
    const lockHeld = () => withOutboxLock({ ifAvailable: true }, (held) => Promise.resolve(!held))
    const stop = startOutboxSync(deps)

    try {
      await until(() => send.mock.calls.length === 1)
      expect(timeout).toHaveBeenCalledWith(SEND_TIMEOUT_MS)
      await vi.advanceTimersByTimeAsync(SEND_TIMEOUT_MS - 1)
      await settle()
      expect(await lockHeld()).toBe(true)

      await vi.advanceTimersByTimeAsync(1)
      await settle()

      expect(signals[0].reason).toMatchObject({ name: 'TimeoutError' })
      expect(await lockHeld()).toBe(false)
      // A timeout is transient: a backoff, never "Precisa de atenção".
      expect(await db.outbox.get('t1')).toEqual(
        item('t1', { attempts: 1, sendAfter: NOW + SEND_TIMEOUT_MS + 2000 }),
      )

      await vi.advanceTimersByTimeAsync(2000)
      await until(() => deps.onSent.mock.calls.length === 1)
      expect(sentIds(send)).toEqual(['t1', 't1'])
      expect(await db.outbox.count()).toBe(0)
    } finally {
      stop()
    }
  })

  it('reports a 401 once and then waits for the next event', async () => {
    useFakeClock()
    await db.outbox.add(item('t1'))
    const deps = startDeps({
      send: vi.fn<SyncDeps['send']>().mockRejectedValue(new ApiError(401, 'auth.required')),
    })
    const stop = startOutboxSync(deps)

    try {
      await until(() => deps.onUnauthorized.mock.calls.length === 1)

      expect(vi.getTimerCount()).toBe(0)
      expect(await db.outbox.get('t1')).toEqual(item('t1'))
    } finally {
      stop()
    }
  })

  it('stops listening, and a run already in flight schedules nothing', async () => {
    useFakeClock()
    await db.outbox.bulkAdd([
      item('t1', { createdAt: 100 }),
      item('t2', { createdAt: 200, sendAfter: NOW + 60_000 }),
    ])
    const { send, release } = holdingFirst()
    const deps = startDeps({ send })
    const stop = startOutboxSync(deps)

    try {
      await until(() => send.mock.calls.length === 1)

      stop()
      release()
      await until(() => deps.onSent.mock.calls.length === 1)
      await settle()
      expect(vi.getTimerCount()).toBe(0)

      window.dispatchEvent(new Event('online'))
      window.dispatchEvent(new Event('focus'))
      window.dispatchEvent(new Event('liberdade:outbox'))
      await vi.advanceTimersByTimeAsync(60_000)
      await settle()
      expect(send).toHaveBeenCalledTimes(1)
    } finally {
      stop()
      release()
    }
  })

  it('stops a run in flight before it sends the next item', async () => {
    await db.outbox.bulkAdd([item('t1', { createdAt: 100 }), item('t2', { createdAt: 200 })])
    const { send, release } = holdingFirst()
    const deps = startDeps({ send })
    const stop = startOutboxSync(deps)

    try {
      await until(() => send.mock.calls.length === 1)

      stop()
      release()
      await until(() => deps.onSent.mock.calls.length > 0)
      await settle()

      expect(sentIds(send)).toEqual(['t1'])
      expect(await db.outbox.toArray()).toEqual([item('t2', { createdAt: 200 })])
    } finally {
      stop()
      release()
    }
  })

  it('does not try again after stop when the run in flight then fails', async () => {
    useFakeClock()
    await db.outbox.add(item('t1'))
    const { send, release } = holdingFirst()
    const onSent = vi.fn<SyncDeps['onSent']>(() => {
      throw new Error('render failed')
    })
    const deps = startDeps({ send, onSent })
    const stop = startOutboxSync(deps)

    try {
      await until(() => send.mock.calls.length === 1)

      stop()
      release()
      await until(() => onSent.mock.calls.length === 1)
      await settle()

      expect(vi.getTimerCount()).toBe(0)
    } finally {
      stop()
      release()
    }
  })

  it('keeps a single timer when several events wake it', async () => {
    useFakeClock()
    await db.outbox.add(item('t1', { sendAfter: NOW + 60_000 }))
    const deps = startDeps()
    const stop = startOutboxSync(deps)

    try {
      await until(() => vi.getTimerCount() === 1)

      window.dispatchEvent(new Event('focus'))
      window.dispatchEvent(new Event('online'))
      await settle()

      expect(vi.getTimerCount()).toBe(1)
      stop()
      expect(vi.getTimerCount()).toBe(0)
    } finally {
      stop()
    }
  })

  it('tries again 5 s after a run fails, without logging', async () => {
    useFakeClock()
    const log = vi.spyOn(console, 'error')
    await db.outbox.bulkAdd([item('t1', { createdAt: 100 }), item('t2', { createdAt: 200 })])
    const onSent = vi.fn<SyncDeps['onSent']>().mockImplementationOnce(() => {
      throw new Error('render failed')
    })
    const deps = startDeps({ onSent })
    const stop = startOutboxSync(deps)

    try {
      await until(() => vi.getTimerCount() === 1)
      expect(sentIds(deps.send)).toEqual(['t1'])
      await vi.advanceTimersByTimeAsync(FAILED_RUN_RETRY_MS - 1)
      await settle()
      expect(deps.send).toHaveBeenCalledTimes(1)

      await vi.advanceTimersByTimeAsync(1)
      await until(() => onSent.mock.calls.length === 2)

      expect(sentIds(deps.send)).toEqual(['t1', 't2'])
      expect(await db.outbox.count()).toBe(0)
      expect(log).not.toHaveBeenCalled()
    } finally {
      stop()
    }
  })

  it('tries again 5 s after onUnauthorized throws', async () => {
    useFakeClock()
    await db.outbox.add(item('t1'))
    const deps = startDeps({
      send: vi.fn<SyncDeps['send']>().mockRejectedValue(new ApiError(401, 'auth.required')),
    })
    deps.onUnauthorized.mockImplementationOnce(() => {
      throw new Error('sign-out failed')
    })
    const stop = startOutboxSync(deps)

    try {
      await until(() => vi.getTimerCount() === 1)
      expect(deps.onUnauthorized).toHaveBeenCalledTimes(1)

      await vi.advanceTimersByTimeAsync(FAILED_RUN_RETRY_MS)
      await until(() => deps.onUnauthorized.mock.calls.length === 2)

      expect(vi.getTimerCount()).toBe(0)
    } finally {
      stop()
    }
  })
})

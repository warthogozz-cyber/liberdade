import { act, cleanup } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_URL } from '@/shared/api'
import { renderHookWithProviders } from '@/shared/testing'
import { onLiveChange } from './liveChanges'
import { isRecentArrival, markLocalChange, resetLocalChanges } from './localChanges'
import { useLiveSync } from './useLiveSync'

const WS = 'ws1'
const URL = `${API_URL}/v1/workspaces/${WS}/events`
const MINUTE = 60_000

class FakeEventSource {
  static instances: FakeEventSource[] = []
  readyState = 0
  closed = false
  private readonly listeners = new Map<string, ((event: Event) => void)[]>()

  readonly url: string
  readonly init?: EventSourceInit

  constructor(url: string, init?: EventSourceInit) {
    this.url = url
    this.init = init
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, listener: (event: Event) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener])
  }

  close() {
    this.closed = true
    this.readyState = 2
  }

  emitOpen() {
    this.readyState = 1
    this.fire('open', new Event('open'))
  }

  emitChanged(data: unknown) {
    this.fire('changed', new MessageEvent('changed', { data }))
  }

  emitReplaced() {
    this.fire('replaced', new MessageEvent('replaced', { data: '{}' }))
  }

  emitError(readyState: number) {
    this.readyState = readyState
    this.fire('error', new Event('error'))
  }

  private fire(type: string, event: Event) {
    for (const listener of this.listeners.get(type) ?? []) listener(event)
  }
}

function createSource(url: string): EventSource {
  return new FakeEventSource(url) as unknown as EventSource
}

function latest(): FakeEventSource {
  const source = FakeEventSource.instances.at(-1)
  if (!source) throw new Error('No source was opened')
  return source
}

function setVisibility(state: 'visible' | 'hidden') {
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(state)
  act(() => {
    document.dispatchEvent(new Event('visibilitychange'))
  })
}

function mount() {
  const rendered = renderHookWithProviders(() => {
    useLiveSync(WS, { createSource })
  })
  const invalidate = vi.spyOn(rendered.queryClient, 'invalidateQueries')
  return { ...rendered, invalidate }
}

function invalidatedKeys(invalidate: { mock: { calls: unknown[][] } }): unknown[] {
  return invalidate.mock.calls.map((call) => (call[0] as { queryKey: unknown }).queryKey)
}

beforeEach(() => {
  vi.useFakeTimers()
  FakeEventSource.instances = []
})

afterEach(() => {
  resetLocalChanges()
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('useLiveSync', () => {
  it('doesNothingWithoutEventSource', () => {
    vi.stubGlobal('EventSource', undefined)
    renderHookWithProviders(() => {
      useLiveSync(WS)
    })
    expect(FakeEventSource.instances).toHaveLength(0)
  })

  it('opensWithCredentials', () => {
    mount()
    expect(latest().url).toBe(URL)
    FakeEventSource.instances = []

    vi.stubGlobal('EventSource', FakeEventSource)
    renderHookWithProviders(() => {
      useLiveSync(WS)
    })
    expect(latest().url).toBe(URL)
    expect(latest().init).toEqual({ withCredentials: true })
  })

  it('debouncesAndInvalidatesByEntity', () => {
    const { invalidate } = mount()
    const source = latest()
    source.emitOpen()

    source.emitChanged('{"entity":"transaction","id":"a"}')
    vi.advanceTimersByTime(50)
    source.emitChanged('{"entity":"transaction","id":"b"}')
    vi.advanceTimersByTime(50)
    source.emitChanged('{"entity":"tag"}')
    expect(invalidate).not.toHaveBeenCalled()

    vi.advanceTimersByTime(250)
    expect(invalidatedKeys(invalidate)).toEqual([
      ['ws', WS, 'transactions'],
      ['ws', WS, 'dashboard'],
      ['ws', WS, 'accounts'],
      ['ws', WS, 'tags'],
      ['ws', WS, 'planning'],
      ['ws', WS, 'gamification'],
    ])
    expect(invalidatedKeys(invalidate)).not.toContainEqual(['ws', WS, 'members'])
  })

  it('refreshesTheSignedInMemberOnAMembersNotice', () => {
    const { invalidate } = mount()
    const source = latest()
    source.emitOpen()

    source.emitChanged('{"entity":"members"}')
    vi.advanceTimersByTime(250)

    expect(invalidatedKeys(invalidate)).toContainEqual(['me'])
    expect(invalidatedKeys(invalidate)).toContainEqual(['ws', WS, 'members'])
  })

  it('tellsListenersEachChange', () => {
    mount()
    const listener = vi.fn()
    const off = onLiveChange(listener)
    latest().emitChanged('{"entity":"transaction","id":"a"}')
    latest().emitChanged('{"entity":"members"}')
    off()

    expect(listener).toHaveBeenNthCalledWith(1, { workspaceId: WS, entity: 'transaction', id: 'a' })
    expect(listener).toHaveBeenNthCalledWith(2, { workspaceId: WS, entity: 'members' })
  })

  it('firstOpenDoesNotRefetch', () => {
    const { invalidate } = mount()
    latest().emitOpen()
    expect(invalidate).not.toHaveBeenCalled()
  })

  it('reopenRefetchesFamily', () => {
    const { invalidate } = mount()
    latest().emitOpen()
    latest().emitOpen()
    expect(invalidatedKeys(invalidate)).toEqual([['ws', WS]])
  })

  it('closesAfterFiveHiddenMinutes', () => {
    const { invalidate } = mount()
    const first = latest()
    first.emitOpen()

    setVisibility('hidden')
    vi.advanceTimersByTime(5 * MINUTE)
    expect(first.closed).toBe(true)

    setVisibility('visible')
    const second = latest()
    expect(second).not.toBe(first)
    second.emitOpen()
    expect(invalidatedKeys(invalidate)).toEqual([['ws', WS]])
  })

  it('shortHideKeepsStream', () => {
    mount()
    const first = latest()
    first.emitOpen()

    setVisibility('hidden')
    vi.advanceTimersByTime(4 * MINUTE)
    setVisibility('visible')
    vi.advanceTimersByTime(2 * MINUTE)

    expect(first.closed).toBe(false)
    expect(FakeEventSource.instances).toHaveLength(1)
  })

  it('closesOffline', () => {
    mount()
    const first = latest()
    first.emitOpen()

    act(() => {
      window.dispatchEvent(new Event('offline'))
    })
    expect(first.closed).toBe(true)

    act(() => {
      window.dispatchEvent(new Event('online'))
    })
    expect(latest()).not.toBe(first)
  })

  it('fallsBackWhenClosed', () => {
    const { invalidate } = mount()
    latest().emitError(2)
    expect(invalidate).not.toHaveBeenCalled()

    setVisibility('hidden')
    setVisibility('visible')
    expect(invalidatedKeys(invalidate)).toEqual([['ws', WS]])
  })

  it('stopsReconnectingWhenReplaced', () => {
    const { invalidate } = mount()
    const first = latest()
    first.emitOpen()
    first.emitReplaced()
    expect(first.closed).toBe(true)

    setVisibility('hidden')
    setVisibility('visible')
    expect(FakeEventSource.instances).toHaveLength(1)
    expect(invalidatedKeys(invalidate)).toEqual([['ws', WS]])

    vi.advanceTimersByTime(MINUTE)
    setVisibility('hidden')
    setVisibility('visible')
    expect(FakeEventSource.instances).toHaveLength(2)
  })

  it('keepsTheStreamWhenTheBrowserRetries', () => {
    mount()
    const first = latest()
    first.emitError(0)
    expect(first.closed).toBe(false)
  })

  it('retriesAtMostOncePerMinute', () => {
    mount()
    latest().emitError(2)
    expect(FakeEventSource.instances).toHaveLength(1)

    for (let i = 0; i < 3; i++) {
      vi.advanceTimersByTime(10_000)
      setVisibility('hidden')
      setVisibility('visible')
    }
    expect(FakeEventSource.instances).toHaveLength(1)

    vi.advanceTimersByTime(31_000)
    setVisibility('hidden')
    setVisibility('visible')
    expect(FakeEventSource.instances).toHaveLength(2)
  })

  it('ignoresMalformedData', () => {
    const { invalidate } = mount()
    const listener = vi.fn()
    const off = onLiveChange(listener)

    latest().emitChanged('x{')
    latest().emitChanged('{"id":"a"}')
    latest().emitChanged('null')
    vi.advanceTimersByTime(1000)
    off()

    expect(invalidate).not.toHaveBeenCalled()
    expect(listener).not.toHaveBeenCalled()
  })

  it('invalidatesTheWholeFamilyForAnUnknownEntity', () => {
    const { invalidate } = mount()
    latest().emitChanged('{"entity":"unicorn"}')
    vi.advanceTimersByTime(250)
    expect(invalidatedKeys(invalidate)).toEqual([['ws', WS]])
  })

  it('localIdIsNotAnArrival', () => {
    mount()
    markLocalChange('t1')
    latest().emitChanged('{"entity":"transaction","id":"t1"}')
    expect(isRecentArrival('t1')).toBe(false)
  })

  it('remoteIdIsAnArrival', () => {
    mount()
    latest().emitChanged('{"entity":"transaction","id":"t2"}')
    expect(isRecentArrival('t2')).toBe(true)
  })

  it('onlyATransactionWithAnIdIsAnArrival', () => {
    mount()
    latest().emitChanged('{"entity":"account","id":"a1"}')
    expect(isRecentArrival('a1')).toBe(false)
  })

  it('closesOnUnmount', () => {
    const { unmount } = mount()
    const source = latest()
    unmount()
    expect(source.closed).toBe(true)
  })
})

import { describe, expect, it, vi } from 'vitest'
import { emitLocalAction, lastLocalActionAt, onLocalAction, type LocalAction } from './localActions'

const action: LocalAction = {
  type: 'transaction-saved',
  workspaceId: 'ws1',
  categoryId: 'cat',
  month: '2026-11',
  fromQueue: false,
}

describe('local actions', () => {
  it('delivers and unsubscribes', () => {
    const listener = vi.fn()
    const off = onLocalAction(listener)

    emitLocalAction(action)
    expect(listener).toHaveBeenCalledExactlyOnceWith(action)

    off()
    emitLocalAction(action)
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('keeps delivering when a listener throws', () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const after = vi.fn()
    const offBad = onLocalAction(() => {
      throw new Error('boom')
    })
    const offAfter = onLocalAction(after)

    emitLocalAction(action)
    expect(after).toHaveBeenCalledOnce()

    offBad()
    offAfter()
    error.mockRestore()
  })

  it('lastLocalActionSkipsQueue', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(5_000)
    try {
      expect(lastLocalActionAt('ws-last')).toBeNull()

      emitLocalAction({ ...action, workspaceId: 'ws-last', fromQueue: true })
      expect(lastLocalActionAt('ws-last')).toBeNull()

      emitLocalAction({ ...action, workspaceId: 'ws-last', fromQueue: false })
      expect(lastLocalActionAt('ws-last')).toBe(5_000)
    } finally {
      vi.useRealTimers()
    }
  })
})

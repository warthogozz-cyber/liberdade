import { describe, expect, it, vi } from 'vitest'
import { notifyQueued, onQueued } from './queuedSignal'

describe('queuedSignal', () => {
  it('notifiesAndUnsubscribes', () => {
    const listener = vi.fn()
    const off = onQueued(listener)
    notifyQueued()
    expect(listener).toHaveBeenCalledTimes(1)

    off()
    notifyQueued()
    expect(listener).toHaveBeenCalledTimes(1)
  })
})

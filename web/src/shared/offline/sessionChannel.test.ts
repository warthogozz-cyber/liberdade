import { afterEach, describe, expect, it, vi } from 'vitest'
import { broadcastSignedIn, broadcastSignedOut, onSessionMessage } from './sessionChannel'
import type { SessionMessage } from './sessionChannel'

/** A BroadcastChannel that delivers to the other open channels of the same name, synchronously. */
class FakeBroadcastChannel {
  static open = new Set<FakeBroadcastChannel>()
  onmessage: ((event: MessageEvent) => void) | null = null
  name: string
  constructor(name: string) {
    this.name = name
    FakeBroadcastChannel.open.add(this)
  }
  postMessage(data: unknown) {
    for (const other of FakeBroadcastChannel.open) {
      if (other !== this && other.name === this.name) other.onmessage?.({ data } as MessageEvent)
    }
  }
  close() {
    FakeBroadcastChannel.open.delete(this)
  }
}

afterEach(() => {
  vi.unstubAllGlobals()
  FakeBroadcastChannel.open.clear()
})

describe('sessionChannel', () => {
  it('deliversMessages', () => {
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel)
    const received: SessionMessage[] = []
    const stop = onSessionMessage((message) => received.push(message))

    broadcastSignedOut()
    broadcastSignedIn('user-1')
    stop()
    broadcastSignedOut()

    expect(received).toEqual([{ type: 'signed-out' }, { type: 'signed-in', userId: 'user-1' }])
    expect(FakeBroadcastChannel.open.size).toBe(0)
  })

  it('ignores messages of another shape', () => {
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel)
    const received: SessionMessage[] = []
    onSessionMessage((message) => received.push(message))
    const other = new FakeBroadcastChannel('liberdade-session')

    for (const data of [null, 'signed-out', {}, { type: 'other' }, { type: 'signed-in' }]) {
      other.postMessage(data)
    }
    other.postMessage({ type: 'signed-in', userId: '' })

    expect(received).toEqual([])
  })

  it('noopWithoutSupport', () => {
    vi.stubGlobal('BroadcastChannel', undefined)
    const handler = vi.fn()

    expect(() => {
      broadcastSignedOut()
      broadcastSignedIn('user-1')
      onSessionMessage(handler)()
    }).not.toThrow()
    expect(handler).not.toHaveBeenCalled()
  })

  it('does not throw when the browser refuses the channel', () => {
    vi.stubGlobal(
      'BroadcastChannel',
      class {
        constructor() {
          throw new Error('blocked')
        }
      },
    )

    expect(() => {
      broadcastSignedOut()
      onSessionMessage(() => {})()
    }).not.toThrow()
  })
})

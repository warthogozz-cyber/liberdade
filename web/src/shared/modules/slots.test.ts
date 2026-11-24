import { lazy } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import { listSlot, registerSlot, resetSlots, type SlotEntry } from './slots'

afterEach(resetSlots)

function entry(key: string): SlotEntry {
  return {
    key,
    module: 'planning',
    roles: ['OWNER'],
    Component: lazy(() => Promise.resolve({ default: () => null })),
  }
}

describe('slots', () => {
  it('lists entries in registration order', () => {
    const first = entry('first')
    const second = entry('second')
    registerSlot('home', first)
    registerSlot('home', second)
    registerSlot('workspace', entry('other'))

    expect(listSlot('home')).toEqual([first, second])
    expect(listSlot('workspace').map((each) => each.key)).toEqual(['other'])
  })

  it('replaces an entry registered again under the same key', () => {
    registerSlot('home', entry('card'))
    const replacement = entry('card')
    registerSlot('home', replacement)

    expect(listSlot('home')).toEqual([replacement])
  })
})

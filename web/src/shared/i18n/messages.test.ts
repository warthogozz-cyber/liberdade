import { describe, expect, it } from 'vitest'
import { defineMessage } from 'react-intl'

describe('message ids', () => {
  it('accepts only known message ids', () => {
    expect(defineMessage({ id: 'app.name' }).id).toBe('app.name')
    // An id of a route bundle type-checks too.
    expect(defineMessage({ id: 'accounts.title' }).id).toBe('accounts.title')
    // @ts-expect-error unknown ids do not type-check
    defineMessage({ id: 'nope.missing' })
  })
})

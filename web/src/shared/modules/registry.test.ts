import { afterEach, describe, expect, it } from 'vitest'
import { listModules, registerModule, resetModules } from './registry'

afterEach(resetModules)

describe('module registry', () => {
  it('registersFakeModule', () => {
    registerModule({ key: 'planning', available: true })

    expect(listModules()).toEqual([{ key: 'planning', available: true }])
  })

  it('replaces a second definition of the same key', () => {
    registerModule({ key: 'planning', available: false })
    registerModule({ key: 'planning', available: true })
    registerModule({ key: 'ventures', available: false })

    expect(listModules()).toEqual([
      { key: 'planning', available: true },
      { key: 'ventures', available: false },
    ])
  })
})

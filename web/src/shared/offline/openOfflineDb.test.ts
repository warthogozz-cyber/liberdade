import { describe, expect, it } from 'vitest'
import { loadOnce, openOfflineDb } from './openOfflineDb'

describe('openOfflineDb', () => {
  it('returns one database to callers that ask at the same time', async () => {
    const [first, second] = await Promise.all([openOfflineDb(), openOfflineDb()])

    expect(second.db).toBe(first.db)
    expect(first.db.name).toBe('liberdade')
  })
})

describe('loadOnce', () => {
  it('loads once for callers that ask at the same time', async () => {
    let loads = 0
    const open = loadOnce(async () => ({ load: ++loads }))

    const [first, second] = await Promise.all([open(), open()])

    expect(loads).toBe(1)
    expect(second).toBe(first)
  })

  it('tries again after a failed load', async () => {
    let loads = 0
    const open = loadOnce(async () => {
      loads += 1
      if (loads === 1) throw new Error('chunk failed to load')
      return { load: loads }
    })

    await expect(open()).rejects.toThrow('chunk failed to load')
    await expect(open()).resolves.toEqual({ load: 2 })
    await expect(open()).resolves.toEqual({ load: 2 })
    expect(loads).toBe(2)
  })
})

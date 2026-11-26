import { afterEach, describe, expect, it, vi } from 'vitest'
import { MARKETS_STORAGE_PREFIX, patchLocation, readLocation, writeLocation } from './locationStore'

const user = '0192f3a4-0000-7000-8000-000000000001'
const key = `liberdade.markets.${user}`

afterEach(() => {
  vi.restoreAllMocks()
  localStorage.clear()
})

describe('locationStore', () => {
  it('usesTheSweptPrefix', () => {
    expect(MARKETS_STORAGE_PREFIX).toBe('liberdade.markets.')
  })

  it('preciseNeverStoresAPoint', () => {
    const kept = writeLocation(user, { mode: 'precise', point: { lat: '-10.12345', lon: '-50.5' } })

    expect(kept).toEqual({ mode: 'precise' })
    expect(JSON.parse(localStorage.getItem(key) ?? '{}')).toEqual({ mode: 'precise' })
    expect(localStorage.getItem(key)).not.toContain('point')
  })

  it('keepsTheApproximateAndTypedPoint', () => {
    writeLocation(user, { mode: 'approximate', point: { lat: '-10.12', lon: '-50.50' } })
    expect(readLocation(user)?.point).toEqual({ lat: '-10.12', lon: '-50.50' })
    writeLocation(user, { mode: 'typed', point: { lat: '-10.123', lon: '-50.500', label: 'Casa' } })
    expect(readLocation(user)?.point?.label).toBe('Casa')
  })

  it('survivesBlockedStorage', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })

    expect(readLocation(user)).toBeNull()
    expect(writeLocation(user, { mode: 'home' })).toEqual({ mode: 'home' })
    expect(patchLocation(user, { mapConsent: true })).toBeNull()
  })

  it('returnsNullForGarbage', () => {
    localStorage.setItem(key, '{not json')
    expect(readLocation(user)).toBeNull()
    localStorage.setItem(key, JSON.stringify({ mode: 'teleport' }))
    expect(readLocation(user)).toBeNull()
  })

  it('consentSurvivesModeChange', () => {
    writeLocation(user, { mode: 'approximate', point: { lat: '-10.12', lon: '-50.50' } })
    patchLocation(user, { mapConsent: true, instagramConsent: true })
    writeLocation(user, { mode: 'precise' })

    expect(readLocation(user)).toEqual({
      mode: 'precise',
      mapConsent: true,
      instagramConsent: true,
    })
  })

  it('patchLocationMergesAndNeedsAChoice', () => {
    expect(patchLocation(user, { mapConsent: true })).toBeNull()
    writeLocation(user, { mode: 'approximate', point: { lat: '-10.12', lon: '-50.50' } })

    expect(patchLocation(user, { mapConsent: true })).toEqual({
      mode: 'approximate',
      point: { lat: '-10.12', lon: '-50.50' },
      mapConsent: true,
    })
  })
})

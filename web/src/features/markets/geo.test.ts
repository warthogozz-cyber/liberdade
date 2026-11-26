import { describe, expect, it } from 'vitest'
import { distanceMeters, formatDistance, roundTo } from './geo'

describe('geo', () => {
  it('searchCenterAlwaysTwoDecimals', () => {
    expect(roundTo(-10.004999, 2)).toBe('-10.00')
    expect(roundTo(-10.005, 2)).toBe('-10.01')
    expect(roundTo(-10, 2)).toBe('-10.00')
    expect(roundTo(-50.12345, 2)).toBe('-50.12')
    expect(roundTo(3.14159, 3)).toBe('3.142')
    expect(roundTo(-0.001, 2)).toBe('0.00')
  })

  it('haversineKnownDistance', () => {
    // 0.01 degrees of longitude on the equator is about 1,112 meters.
    const meters = distanceMeters({ lat: 0, lon: 0 }, { lat: 0, lon: 0.01 })
    expect(Math.abs(meters - 1112)).toBeLessThanOrEqual(1)
    expect(distanceMeters({ lat: -10, lon: -50 }, { lat: -10, lon: -50 })).toBe(0)
  })

  it('formatsDistance', () => {
    expect(formatDistance(447, false)).toBe('450 m')
    expect(formatDistance(1234, false)).toBe('1,2 km')
    expect(formatDistance(1234, true)).toBe('cerca de 1,2 km')
    expect(formatDistance(447, true)).toBe('cerca de 450 m')
  })
})

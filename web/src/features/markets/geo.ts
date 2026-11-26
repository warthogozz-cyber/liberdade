import { marketsMessages } from './messages.pt-BR'

/** A mean Earth radius in meters, as the API's own distance uses. */
const EARTH_RADIUS_M = 6_371_008.8

export type GeoPoint = { lat: number; lon: number }

/**
 * Rounds to a fixed number of decimals, half away from zero, on the number's decimal text (so
 * `-10.005` is `"-10.01"`, not what the binary value would give) and always with the decimals.
 */
export function roundTo(value: number, decimals: 2 | 3): string {
  const text = String(Math.abs(value))
  if (text.includes('e')) {
    // Too small (or too large) for plain text: nothing a coordinate needs rounding for.
    return (Math.abs(value) < 1 ? 0 : value).toFixed(decimals)
  }
  const [whole = '0', fraction = ''] = text.split('.')
  let scaled = BigInt(whole + fraction.padEnd(decimals, '0').slice(0, decimals))
  if ((fraction[decimals] ?? '0') >= '5') scaled += 1n
  const digits = scaled.toString().padStart(decimals + 1, '0')
  const sign = value < 0 && scaled !== 0n ? '-' : ''
  return `${sign}${digits.slice(0, -decimals)}.${digits.slice(-decimals)}`
}

const toRadians = (degrees: number) => (degrees * Math.PI) / 180

/** The straight-line distance in meters between two points (haversine). */
export function distanceMeters(a: GeoPoint, b: GeoPoint): number {
  const dLat = toRadians(b.lat - a.lat)
  const dLon = toRadians(b.lon - a.lon)
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(a.lat)) * Math.cos(toRadians(b.lat)) * Math.sin(dLon / 2) ** 2
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(h)))
}

/**
 * "450 m" below a kilometer (to the nearest 10 m), "1,2 km" from there on; "cerca de " in front
 * when the position it was measured from is only approximate.
 */
export function formatDistance(meters: number, approximate: boolean): string {
  const nearest10 = Math.round(meters / 10) * 10
  const text =
    nearest10 < 1000
      ? `${nearest10} m`
      : `${(Math.round(meters / 100) / 10).toFixed(1).replace('.', ',')} km`
  return approximate ? marketsMessages['markets.distance.about'].replace('{distance}', text) : text
}

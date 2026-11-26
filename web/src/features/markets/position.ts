import type { GeoPoint } from './geo'

export type PositionError = 'denied' | 'unavailable'

/** A hung reading must not leave the screen waiting forever. */
const POSITION_TIMEOUT_MS = 15_000

/** One reading of the device's position; the browser asks the person only now. */
export function readPosition(options: PositionOptions): Promise<GeoPoint> {
  return new Promise((resolve, reject) => {
    if (typeof navigator === 'undefined' || !('geolocation' in navigator)) {
      reject(new Error('unavailable' satisfies PositionError))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({ lat: position.coords.latitude, lon: position.coords.longitude }),
      (error) =>
        reject(new Error((error.code === 1 ? 'denied' : 'unavailable') satisfies PositionError)),
      { timeout: POSITION_TIMEOUT_MS, ...options },
    )
  })
}

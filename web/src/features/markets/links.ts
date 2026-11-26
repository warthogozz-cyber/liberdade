/** Where a point opens in a map app; every one goes out as a link that opens a new tab, without the opener. */
export const mapsSearchUrl = (lat: number | string, lon: number | string) =>
  `https://www.google.com/maps/search/?api=1&query=${lat}%2C${lon}`

export const mapsDirectionsUrl = (lat: number | string, lon: number | string) =>
  `https://www.google.com/maps/dir/?api=1&destination=${lat}%2C${lon}`

export const wazeUrl = (lat: number | string, lon: number | string) =>
  `https://waze.com/ul?ll=${lat},${lon}&navigate=yes`

/** The longest address a link field takes. */
export const MAX_LINK_LENGTH = 500
/** The longest name of a flyer's source. */
export const MAX_FLYER_SOURCE_LENGTH = 60

/** Only an `https://` address without credentials, up to 500 characters (the API's own rule, D7). */
export function isValidHttpsUrl(text: string): boolean {
  if (!text.startsWith('https://') || text.length > MAX_LINK_LENGTH) return false
  try {
    const url = new URL(text)
    return url.protocol === 'https:' && url.hostname !== '' && !url.username && !url.password
  } catch {
    return false
  }
}

/** Only a value that is an `https://` address becomes a link on the card. */
export const isSafeLink = (value: string | null | undefined): value is string =>
  value != null && value.startsWith('https://')

const HANDLE = /^[A-Za-z0-9._]{1,30}$/
const POST_CODE = /^[A-Za-z0-9_-]+$/

/**
 * An Instagram profile or post as the API takes it: `@profile`, `profile`, the profile address, or
 * a post or reel address (D7).
 */
export function isValidInstagram(text: string): boolean {
  if (!text.includes('/')) return HANDLE.test(text.startsWith('@') ? text.slice(1) : text)
  if (text.length > MAX_LINK_LENGTH) return false
  let url: URL
  try {
    url = new URL(text)
  } catch {
    return false
  }
  if (url.protocol !== 'https:' || !/^(www\.)?instagram\.com$/.test(url.hostname)) return false
  if (url.username || url.password) return false
  const [first, second, ...rest] = url.pathname.split('/').filter(Boolean)
  if (first === undefined) return false
  if (first === 'p' || first === 'reel') {
    return second !== undefined && POST_CODE.test(second) && rest.length === 0
  }
  return second === undefined && HANDLE.test(first)
}

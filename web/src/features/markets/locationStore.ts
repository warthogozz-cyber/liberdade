/**
 * Where the person looks for markets, kept on this device, per person (`liberdade.markets.<userId>`).
 * A precise position is never stored: only an approximate point (2 decimals) or a typed address's
 * point (3 decimals). The screens work without storage: every call here survives it being blocked.
 */

export type LocationMode = 'precise' | 'approximate' | 'typed' | 'home'

export type StoredPoint = { lat: string; lon: string; label?: string }

export type StoredLocation = {
  mode: LocationMode
  point?: StoredPoint
  /** The person already accepted the third-party notice for the map ("do not ask again"). */
  mapConsent?: true
  /** The same, for the Instagram embed. */
  instagramConsent?: true
}

/** The prefix of every key this feature keeps; the sign-out sweep in `shared/offline` uses the same text. */
export const MARKETS_STORAGE_PREFIX = 'liberdade.markets.'

const MODES: readonly string[] = ['precise', 'approximate', 'typed', 'home']
const POINT_MODES: readonly LocationMode[] = ['approximate', 'typed']

const keyFor = (userId: string) => `${MARKETS_STORAGE_PREFIX}${userId}`

function isPoint(value: unknown): value is StoredPoint {
  if (typeof value !== 'object' || value === null) return false
  const point = value as Record<string, unknown>
  return (
    typeof point.lat === 'string' &&
    typeof point.lon === 'string' &&
    (point.label === undefined || typeof point.label === 'string')
  )
}

/** The saved choice, or null when there is none, it is unreadable or storage is blocked. */
export function readLocation(userId: string): StoredLocation | null {
  try {
    const text = localStorage.getItem(keyFor(userId))
    if (text === null) return null
    const parsed: unknown = JSON.parse(text)
    if (typeof parsed !== 'object' || parsed === null) return null
    const raw = parsed as Record<string, unknown>
    if (typeof raw.mode !== 'string' || !MODES.includes(raw.mode)) return null
    const mode = raw.mode as LocationMode
    return {
      mode,
      ...(POINT_MODES.includes(mode) && isPoint(raw.point) ? { point: raw.point } : {}),
      ...(raw.mapConsent === true ? { mapConsent: true as const } : {}),
      ...(raw.instagramConsent === true ? { instagramConsent: true as const } : {}),
    }
  } catch {
    return null
  }
}

/**
 * Saves a choice and returns what was kept. A precise (or family address) choice drops its point,
 * and the consents already given stay across a change of mode.
 */
export function writeLocation(userId: string, value: StoredLocation): StoredLocation {
  const previous = readLocation(userId)
  const next: StoredLocation = {
    mode: value.mode,
    ...(POINT_MODES.includes(value.mode) && value.point ? { point: value.point } : {}),
    ...(value.mapConsent === true || previous?.mapConsent === true
      ? { mapConsent: true as const }
      : {}),
    ...(value.instagramConsent === true || previous?.instagramConsent === true
      ? { instagramConsent: true as const }
      : {}),
  }
  try {
    localStorage.setItem(keyFor(userId), JSON.stringify(next))
  } catch {
    // Without storage the choice lasts as long as the screen does.
  }
  return next
}

/** Merges fields into the saved choice (a consent, for one); does nothing without a choice. */
export function patchLocation(
  userId: string,
  patch: Partial<StoredLocation>,
): StoredLocation | null {
  const current = readLocation(userId)
  return current === null ? null : writeLocation(userId, { ...current, ...patch })
}

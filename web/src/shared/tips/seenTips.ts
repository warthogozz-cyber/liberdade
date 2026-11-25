/** The tips of the app, one per screen that has one. */
export type TipId =
  | 'dashboard'
  | 'transactions'
  | 'accounts'
  | 'planning'
  | 'indicators'
  | 'calculator'
  | 'import'
  | 'achievements'

const key = (userId: string, tipId: string) => `liberdade.tip:${userId}:${tipId}`

/** Whether this person already dismissed the tip on this device; false when storage is unavailable. */
export function hasSeenTip(userId: string, tipId: string): boolean {
  try {
    return localStorage.getItem(key(userId, tipId)) === '1'
  } catch {
    return false
  }
}

/** Remembers that this person dismissed the tip; without storage it only lasts until the screen closes. */
export function markTipSeen(userId: string, tipId: string): void {
  try {
    localStorage.setItem(key(userId, tipId), '1')
  } catch {
    // Nothing to remember it with.
  }
}

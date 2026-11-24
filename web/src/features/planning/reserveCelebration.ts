import type { ReserveView } from '@/shared/api/planning'

/** The reserve covers its target: there are reserve accounts and the months reach the target (D12). */
export function reserveReached(view: ReserveView): boolean {
  return view.hasReserveAccounts && view.months !== null && view.months >= view.targetMonths
}

export function reserveStorageKey(ws: string): string {
  return `liberdade.reserve-celebrated:${ws}`
}

/** Workspaces celebrated in this session, for when the device storage fails. */
const celebratedInSession = new Set<string>()

function isMarked(ws: string): boolean {
  if (celebratedInSession.has(ws)) return true
  try {
    return localStorage.getItem(reserveStorageKey(ws)) !== null
  } catch {
    return false
  }
}

function setMark(ws: string, marked: boolean): void {
  if (marked) celebratedInSession.add(ws)
  else celebratedInSession.delete(ws)
  try {
    if (marked) localStorage.setItem(reserveStorageKey(ws), '1')
    else localStorage.removeItem(reserveStorageKey(ws))
  } catch {
    // Without storage a new session may celebrate again; nothing else depends on it.
  }
}

/**
 * Whether to celebrate now. Reaching the target celebrates once and leaves a mark; a reading below
 * the target clears the mark, so the next arrival celebrates again (D12).
 */
export function shouldCelebrateReserve(ws: string, view: ReserveView): boolean {
  if (!reserveReached(view)) {
    if (view.hasReserveAccounts && view.months !== null) setMark(ws, false)
    return false
  }
  if (isMarked(ws)) return false
  setMark(ws, true)
  return true
}

import { useCallback, useSyncExternalStore } from 'react'

const TIP_PREFIX = 'liberdade.tip:'
const TIPS_KEY = 'liberdade.tips'

const listeners = new Set<() => void>()
// What this page remembers when `localStorage` is unavailable; it lasts until the page closes.
const memorySeen = new Set<string>()
let memoryTipsOn = true
// Set when the last write of the switch failed, so the memory value wins over what storage holds.
let tipsWriteFailed = false
// How many bubbles, tours and event tips are on the screen; a count, so one closing keeps the rest open.
let helpOpenCount = 0

const seenKey = (userId: string, id: string) => `${TIP_PREFIX}${userId}:${id}`

function emit(): void {
  for (const listener of [...listeners]) listener()
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  const onStorage = (event: StorageEvent) => {
    if (event.key === null || event.key.startsWith(TIP_PREFIX) || event.key === TIPS_KEY) listener()
  }
  window.addEventListener('storage', onStorage)
  return () => {
    listeners.delete(listener)
    window.removeEventListener('storage', onStorage)
  }
}

/** Whether this person already dismissed the tip or tour on this device. */
export function hasSeen(userId: string, id: string): boolean {
  const key = seenKey(userId, id)
  try {
    return localStorage.getItem(key) === '1' || memorySeen.has(key)
  } catch {
    return memorySeen.has(key)
  }
}

/** Remembers that this person saw it; without storage it lasts until the page closes. */
export function markSeen(userId: string, id: string): void {
  const key = seenKey(userId, id)
  try {
    localStorage.setItem(key, '1')
  } catch {
    memorySeen.add(key)
  }
  emit()
}

/** Forgets one tip or tour for this person, so it shows again. */
export function clearSeen(userId: string, id: string): void {
  const key = seenKey(userId, id)
  memorySeen.delete(key)
  try {
    localStorage.removeItem(key)
  } catch {
    // Nothing stored to forget.
  }
  emit()
}

/** Forgets every tip and tour of this person; other people and the tips switch are untouched. */
export function resetAllSeen(userId: string): void {
  const prefix = `${TIP_PREFIX}${userId}:`
  for (const key of [...memorySeen]) if (key.startsWith(prefix)) memorySeen.delete(key)
  try {
    const keys: string[] = []
    for (let i = 0; i < localStorage.length; i += 1) {
      const key = localStorage.key(i)
      if (key?.startsWith(prefix)) keys.push(key)
    }
    for (const key of keys) localStorage.removeItem(key)
  } catch {
    // Nothing stored to forget.
  }
  emit()
}

/** Whether this person saw the tip or tour; re-renders when it changes, here or in another tab. */
export function useSeen(userId: string | undefined, id: string): boolean {
  const snapshot = useCallback(() => (userId ? hasSeen(userId, id) : false), [userId, id])
  return useSyncExternalStore(subscribe, snapshot, () => false)
}

/** Whether the first visit tips show: on by default, and on when storage is unavailable. */
export function isTipsEnabled(): boolean {
  if (tipsWriteFailed) return memoryTipsOn
  try {
    return localStorage.getItem(TIPS_KEY) !== 'off'
  } catch {
    return memoryTipsOn
  }
}

export function setTipsEnabled(on: boolean): void {
  memoryTipsOn = on
  try {
    if (on) localStorage.removeItem(TIPS_KEY)
    else localStorage.setItem(TIPS_KEY, 'off')
    tipsWriteFailed = false
  } catch {
    tipsWriteFailed = true
    // Without storage the choice only lasts until the page closes.
  }
  emit()
}

/** The tips switch of this device, shared by every component that reads it. */
export function useTipsEnabled(): [boolean, (on: boolean) => void] {
  const enabled = useSyncExternalStore(subscribe, isTipsEnabled, () => true)
  return [enabled, setTipsEnabled]
}

/** Marks that a bubble, tour or event tip opened (true) or closed (false), so only one shows at a time. */
export function setHelpOpen(open: boolean): void {
  helpOpenCount = open ? helpOpenCount + 1 : Math.max(0, helpOpenCount - 1)
  emit()
}

export function isHelpOpen(): boolean {
  return helpOpenCount > 0
}

/** Whether a bubble, tour or event tip holds the slot; re-renders when it is taken or let go. */
export function useHelpOpen(): boolean {
  return useSyncExternalStore(subscribe, isHelpOpen, () => false)
}

/** Forgets every open surface; for tests. */
export function resetHelpOpenForTests(): void {
  helpOpenCount = 0
  emit()
}

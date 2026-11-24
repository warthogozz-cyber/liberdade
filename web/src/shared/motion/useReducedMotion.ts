import { useSyncExternalStore } from 'react'

const QUERY = '(prefers-reduced-motion: reduce)'

function subscribe(onChange: () => void): () => void {
  if (typeof window === 'undefined' || !window.matchMedia) return () => undefined
  const list = window.matchMedia(QUERY)
  // A minimal MediaQueryList (as some embedded webviews and test stubs give) may not emit changes.
  list.addEventListener?.('change', onChange)
  return () => list.removeEventListener?.('change', onChange)
}

function getSnapshot(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia(QUERY).matches
}

/** Whether the person asked the system for less motion; false where there is no `matchMedia`. */
export function useReducedMotion(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, () => false)
}

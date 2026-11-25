import { useCallback, useState } from 'react'

const KEY = 'liberdade.tips'

/** Whether the first visit tips show: on by default, and on when storage is unavailable. */
export function isTipsEnabled(): boolean {
  try {
    return localStorage.getItem(KEY) !== 'off'
  } catch {
    return true
  }
}

function store(on: boolean): void {
  try {
    if (on) localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, 'off')
  } catch {
    // Without storage the choice only lasts until the screen closes.
  }
}

/** The tips preference of this device, kept in `localStorage`. */
export function useTipsEnabled(): [boolean, (on: boolean) => void] {
  const [enabled, setEnabled] = useState(isTipsEnabled)
  const set = useCallback((on: boolean) => {
    store(on)
    setEnabled(on)
  }, [])
  return [enabled, set]
}

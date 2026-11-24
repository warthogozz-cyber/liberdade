import { useCallback, useState } from 'react'

const KEY = 'liberdade.sound'

/** Whether the achievement sound is on: off by default, and off when storage is unavailable. */
export function isSoundEnabled(): boolean {
  try {
    return localStorage.getItem(KEY) === 'on'
  } catch {
    return false
  }
}

function store(on: boolean): void {
  try {
    if (on) localStorage.setItem(KEY, 'on')
    else localStorage.removeItem(KEY)
  } catch {
    // Without storage the choice only lasts until the screen closes.
  }
}

/** The sound preference of this device, kept in `localStorage`. */
export function useSoundEnabled(): [boolean, (on: boolean) => void] {
  const [enabled, setEnabled] = useState(isSoundEnabled)
  const set = useCallback((on: boolean) => {
    store(on)
    setEnabled(on)
  }, [])
  return [enabled, set]
}

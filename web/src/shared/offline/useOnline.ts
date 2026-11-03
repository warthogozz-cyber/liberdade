import { useSyncExternalStore } from 'react'

function subscribe(onChange: () => void): () => void {
  window.addEventListener('online', onChange)
  window.addEventListener('offline', onChange)
  return () => {
    window.removeEventListener('online', onChange)
    window.removeEventListener('offline', onChange)
  }
}

function isOnline(): boolean {
  return navigator.onLine
}

/** Whether the browser believes it is online, updated on the `online` and `offline` events. */
export function useOnline(): boolean {
  return useSyncExternalStore(subscribe, isOnline)
}

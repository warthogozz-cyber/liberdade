import { createContext, useContext } from 'react'

export interface ToastOptions {
  message: string
  action?: { label: string; altText: string; onClick: () => void }
}

export interface ToastApi {
  /** Shows `toast`, replacing the one on screen. */
  show(toast: ToastOptions): void
}

export const ToastContext = createContext<ToastApi | null>(null)

/** Whether a notice is on the screen. Apart from `ToastContext`, so `show` callers keep still. */
export const ToastShowingContext = createContext(false)

/** Shows short notices at the bottom of the screen. Needs the `ToastProvider` above. */
export function useToast(): ToastApi {
  const toast = useContext(ToastContext)
  if (!toast) throw new Error('useToast needs a ToastProvider above it')
  return toast
}

/** Whether a notice is on the screen, so another card at the bottom can wait for its place. */
export function useToastShowing(): boolean {
  return useContext(ToastShowingContext)
}

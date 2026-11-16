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

/** Shows short notices at the bottom of the screen. Needs the `ToastProvider` above. */
export function useToast(): ToastApi {
  const toast = useContext(ToastContext)
  if (!toast) throw new Error('useToast needs a ToastProvider above it')
  return toast
}

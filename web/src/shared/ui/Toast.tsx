import { Toast } from 'radix-ui'
import { useMemo, useRef, useState, type ReactNode } from 'react'
import { useIntl } from 'react-intl'
import { ToastContext, ToastShowingContext, type ToastApi, type ToastOptions } from './useToast'

const DURATION_MS = 5000

/**
 * One notice at a time, for `DURATION_MS`: a new one replaces the one on screen. It sits above the
 * router, so a notice outlives the screen that showed it.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const intl = useIntl()
  const nextId = useRef(0)
  const [current, setCurrent] = useState<(ToastOptions & { id: number }) | null>(null)
  const api = useMemo<ToastApi>(
    () => ({
      show(toast) {
        nextId.current += 1
        setCurrent({ ...toast, id: nextId.current })
      },
    }),
    [],
  )
  const region = intl.formatMessage({ id: 'toast.region' })

  return (
    <ToastContext.Provider value={api}>
      <Toast.Provider duration={DURATION_MS} label={region}>
        <ToastShowingContext.Provider value={current !== null}>
          {children}
        </ToastShowingContext.Provider>
        {current && (
          <Toast.Root
            // A new key mounts a fresh notice, with its own timer.
            key={current.id}
            onOpenChange={(open) => {
              if (!open) setCurrent((shown) => (shown?.id === current.id ? null : shown))
            }}
            className="flex min-h-12 items-center justify-between gap-4 rounded-field bg-text px-4 py-3 text-surface shadow-lg"
          >
            <Toast.Title>{current.message}</Toast.Title>
            {current.action && (
              <Toast.Action
                altText={current.action.altText}
                onClick={current.action.onClick}
                className="min-h-12 shrink-0 px-2 font-semibold underline focus-visible:outline-2 focus-visible:outline-offset-2"
              >
                {current.action.label}
              </Toast.Action>
            )}
          </Toast.Root>
        )}
        <Toast.Viewport
          label={region}
          className="fixed inset-x-0 bottom-20 z-50 mx-auto flex max-w-lg flex-col gap-2 p-4 outline-none"
        />
      </Toast.Provider>
    </ToastContext.Provider>
  )
}

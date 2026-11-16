import { lazy, Suspense, useCallback, useRef, useState, type ReactNode } from 'react'
import { ReauthContext, type ReauthOutcome } from './useReauth'

// The dialog and the sign-in code behind it load only when an action asks for them (D27).
const ReauthDialog = lazy(() =>
  import('./ReauthDialog').then((module) => ({ default: module.ReauthDialog })),
)

interface ReauthProviderProps {
  children: ReactNode
  /** Where a full page load goes: Google, or `/inicio` when the session turned out to be another person's. */
  assign?: (url: string) => void
}

interface Pending {
  promise: Promise<ReauthOutcome>
  settle: (outcome: ReauthOutcome) => void
}

/** Owns the re-authentication dialog that `useReauth` opens. */
export function ReauthProvider({
  children,
  assign = (url) => window.location.assign(url),
}: ReauthProviderProps) {
  const [open, setOpen] = useState(false)
  const pending = useRef<Pending>(undefined)

  // Actions that wait at once share one dialog and one answer.
  const request = useCallback((): Promise<ReauthOutcome> => {
    if (pending.current) return pending.current.promise
    let settle: (outcome: ReauthOutcome) => void = () => {}
    const promise = new Promise<ReauthOutcome>((resolve) => {
      settle = resolve
    })
    pending.current = { promise, settle }
    setOpen(true)
    return promise
  }, [])

  function finish(outcome: ReauthOutcome) {
    const current = pending.current
    pending.current = undefined
    setOpen(false)
    current?.settle(outcome)
  }

  return (
    <ReauthContext.Provider value={request}>
      {children}
      {open && (
        <Suspense fallback={null}>
          <ReauthDialog assign={assign} onFinish={finish} />
        </Suspense>
      )}
    </ReauthContext.Provider>
  )
}

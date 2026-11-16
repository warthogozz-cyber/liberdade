import {
  Component,
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { ReauthCancelledError } from '@/shared/api'
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
  fail: (error: unknown) => void
}

/** Keeps a dialog that fails to load (or to render) from taking the whole app with it. */
class DialogBoundary extends Component<
  { onError: (error: unknown) => void; children: ReactNode },
  { failed: boolean }
> {
  state = { failed: false }
  static getDerivedStateFromError() {
    return { failed: true }
  }
  componentDidCatch(error: unknown) {
    this.props.onError(error)
  }
  render() {
    return this.state.failed ? null : this.props.children
  }
}

/** Owns the re-authentication dialog that `useReauth` opens. */
export function ReauthProvider({
  children,
  assign = (url) => window.location.assign(url),
}: ReauthProviderProps) {
  const [round, setRound] = useState(0)
  const [open, setOpen] = useState(false)
  const pending = useRef<Pending>(undefined)

  // Unmounting with a request waiting answers it, so no action hangs.
  useEffect(
    () => () => {
      pending.current?.fail(new ReauthCancelledError())
      pending.current = undefined
    },
    [],
  )

  // Actions that wait at once share one dialog and one answer.
  const request = useCallback((): Promise<ReauthOutcome> => {
    if (pending.current) return pending.current.promise
    let settle: (outcome: ReauthOutcome) => void = () => {}
    let fail: (error: unknown) => void = () => {}
    const promise = new Promise<ReauthOutcome>((resolve, reject) => {
      settle = resolve
      fail = reject
    })
    pending.current = { promise, settle, fail }
    setRound((value) => value + 1)
    setOpen(true)
    return promise
  }, [])

  function finish(outcome: ReauthOutcome) {
    const current = pending.current
    pending.current = undefined
    setOpen(false)
    current?.settle(outcome)
  }

  function failed(error: unknown) {
    const current = pending.current
    pending.current = undefined
    setOpen(false)
    current?.fail(error)
  }

  return (
    <ReauthContext.Provider value={request}>
      {children}
      {open && (
        <DialogBoundary key={round} onError={failed}>
          <Suspense fallback={null}>
            <ReauthDialog assign={assign} onFinish={finish} />
          </Suspense>
        </DialogBoundary>
      )}
    </ReauthContext.Provider>
  )
}

import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { FormattedMessage } from 'react-intl'
import { getMe, queryKeys, resetCsrf, type MeView } from '@/shared/api'
import { clearSession } from '@/shared/offline'
import { Sheet } from '@/shared/ui'
import { startGoogleSignIn } from './google'
import { signInWithPasskey } from './passkey'
import { saveReturnPath } from './returnPath'
import type { ReauthOutcome } from './useReauth'

interface ReauthDialogProps {
  /** Where a full page load goes. */
  assign: (url: string) => void
  onFinish: (outcome: ReauthOutcome) => void
}

const button =
  'min-h-12 w-full rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

/**
 * Asks the person to sign in again before a sensitive action (US-01). A passkey confirms on the
 * spot; Google leaves the page and the person repeats the action on return. Loaded on demand.
 */
export function ReauthDialog({ assign, onFinish }: ReauthDialogProps) {
  const queryClient = useQueryClient()
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const [failed, setFailed] = useState(false)

  async function confirmWithPasskey() {
    if (busyRef.current) return
    busyRef.current = true
    setBusy(true)
    setFailed(false)
    try {
      const before = queryClient.getQueryData<MeView>(queryKeys.me)?.user.id
      await signInWithPasskey()
      const me = await getMe()
      if (before !== undefined && me.user.id !== before) {
        // The passkey belongs to somebody else: nothing of the previous person stays, and the action
        // does not run again.
        await clearSession(queryClient)
        onFinish('replaced')
        assign('/inicio')
        return
      }
      // A new sign-in starts a new session, so the CSRF token is fetched again.
      resetCsrf()
      queryClient.setQueryData(queryKeys.me, me)
      onFinish('confirmed')
    } catch {
      setFailed(true)
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }

  function confirmWithGoogle() {
    saveReturnPath(`${window.location.pathname}${window.location.search}${window.location.hash}`)
    startGoogleSignIn({ reauth: true }, assign)
  }

  const cancel = () => onFinish('cancelled')

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) cancel()
      }}
      title={<FormattedMessage id="reauth.title" />}
    >
      <p className="text-text-muted">
        <FormattedMessage id="reauth.body" />
      </p>
      {failed && (
        <p role="alert" className="mt-3 text-negative">
          <FormattedMessage id="reauth.failed" />
        </p>
      )}
      <div className="mt-6 flex flex-col gap-2">
        <button
          type="button"
          className={`${button} bg-primary text-on-primary`}
          disabled={busy}
          onClick={() => void confirmWithPasskey()}
        >
          <FormattedMessage id="reauth.passkey" />
        </button>
        <button
          type="button"
          className={`${button} border border-primary text-primary`}
          disabled={busy}
          onClick={confirmWithGoogle}
        >
          <FormattedMessage id="reauth.google" />
        </button>
        <button type="button" className={`${button} text-text-muted`} onClick={cancel}>
          <FormattedMessage id="reauth.cancel" />
        </button>
      </div>
    </Sheet>
  )
}

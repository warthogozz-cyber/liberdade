import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { FormattedMessage } from 'react-intl'
import { useNavigate } from 'react-router'
import { postJson, useMe } from '@/shared/api'
import {
  broadcastSignedOut,
  clearOutboxForUser,
  clearSession,
  countOutboxForUser,
  useOnline,
} from '@/shared/offline'
import { ConfirmDialog } from '@/shared/ui'

/**
 * Signs out on the API, then forgets the person's queue and data on this device (D19). With expenses
 * still queued, it asks first, since signing out deletes them.
 */
export function SignOutButton() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const online = useOnline()
  const userId = useMe().data?.user.id
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)
  const [failed, setFailed] = useState(false)
  // How many expenses are queued, while the dialog asks about them.
  const [pending, setPending] = useState(0)

  function claim(): boolean {
    if (busyRef.current) return false
    busyRef.current = true
    setBusy(true)
    setFailed(false)
    return true
  }

  function release(): void {
    busyRef.current = false
    setBusy(false)
  }

  // This order is kept on purpose (T10-signout-order): leaving the screens stops the sync before the
  // cache goes.
  async function signOut(id: string) {
    try {
      await postJson('/logout')
      await clearOutboxForUser(id)
      await navigate('/entrar')
      await clearSession(queryClient)
      // The other tabs leave too (D26); sent last, so this tab's own cleanup keeps its order.
      broadcastSignedOut()
    } catch {
      setFailed(true)
      release()
    }
  }

  async function start() {
    if (!userId || !claim()) return
    let count: number
    try {
      count = await countOutboxForUser(userId)
    } catch {
      // Without the count there is no telling whether queued expenses would be lost, so nothing else
      // runs (T10-signout-count).
      setFailed(true)
      release()
      return
    }
    if (count > 0) {
      setPending(count)
      release()
      return
    }
    await signOut(userId)
  }

  function confirm() {
    setPending(0)
    if (userId && claim()) void signOut(userId)
  }

  return (
    <div className="flex flex-col gap-2">
      <button
        type="button"
        className="min-h-12 rounded-field border border-negative px-4 font-medium text-negative focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
        disabled={busy || !online || !userId}
        onClick={() => void start()}
      >
        <FormattedMessage id="signOut.button" />
      </button>
      {!online && (
        <p className="text-sm text-text-muted">
          <FormattedMessage id="signOut.offline" />
        </p>
      )}
      {failed && (
        <p role="alert" className="text-sm text-negative">
          <FormattedMessage id="signOut.failed" />
        </p>
      )}
      <ConfirmDialog
        open={pending > 0}
        title={<FormattedMessage id="signOut.pendingTitle" />}
        body={<FormattedMessage id="signOut.pendingBody" values={{ count: pending }} />}
        confirmLabel={<FormattedMessage id="signOut.confirm" />}
        cancelLabel={<FormattedMessage id="signOut.cancel" />}
        onConfirm={confirm}
        onCancel={() => setPending(0)}
      />
    </div>
  )
}

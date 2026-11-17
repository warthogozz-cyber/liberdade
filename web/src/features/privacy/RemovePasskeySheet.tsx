import { useRef, useState } from 'react'
import { useIntl } from 'react-intl'
import { ApiError, ReauthCancelledError } from '@/shared/api'
import { useRemovePasskey, type PasskeyView } from '@/shared/api/privacy'
import { useReauth } from '@/shared/auth'
import { useOnline } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { Sheet } from '@/shared/ui/Sheet'
import { dangerButton } from './buttons'
import './register'

/** Asks before removing a passkey. The person's last way in is refused with a clear message. */
export function RemovePasskeySheet({
  passkey,
  onClose,
}: {
  passkey: PasskeyView
  onClose: () => void
}) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const run = useReauth()
  const remove = useRemovePasskey()
  const [error, setError] = useState<string>()
  // The reauth dialog keeps `run` going after the 403, so the mutation's own flag is not enough.
  const [busy, setBusy] = useState(false)
  const working = useRef(false)

  async function confirm() {
    if (!online || working.current) return
    working.current = true
    setBusy(true)
    setError(undefined)
    try {
      await run(() => remove.mutateAsync(passkey.id))
    } catch (failure) {
      if (failure instanceof ReauthCancelledError) return
      if (failure instanceof ApiError && failure.code === 'passkey.last-method') {
        setError(intl.formatMessage({ id: 'privacy.passkey.lastMethod' }))
        return
      }
      setError(
        (failure instanceof ApiError && failure.detail) ||
          intl.formatMessage({ id: 'common.actionFailed' }),
      )
      return
    } finally {
      working.current = false
      setBusy(false)
    }
    toast.show({ message: intl.formatMessage({ id: 'privacy.passkey.removed' }) })
    onClose()
  }

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      title={intl.formatMessage({ id: 'privacy.passkey.removeTitle' }, { name: passkey.label })}
    >
      <div className="flex flex-col gap-4">
        <p>{intl.formatMessage({ id: 'privacy.passkey.removeBody' })}</p>
        {error && (
          <p role="alert" className="text-negative">
            {error}
          </p>
        )}
        {!online && (
          <p className="text-text-muted">
            {intl.formatMessage({ id: 'privacy.passkeys.offline' })}
          </p>
        )}
        <button
          type="button"
          disabled={!online || busy}
          onClick={() => void confirm()}
          className={dangerButton}
        >
          {intl.formatMessage({ id: 'privacy.passkey.remove' })}
        </button>
      </div>
    </Sheet>
  )
}

import { useState } from 'react'
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

  async function confirm() {
    if (!online || remove.isPending) return
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
          <p className="text-text-muted">{intl.formatMessage({ id: 'privacy.offline' })}</p>
        )}
        <button
          type="button"
          disabled={!online || remove.isPending}
          onClick={() => void confirm()}
          className={dangerButton}
        >
          {intl.formatMessage({ id: 'privacy.passkey.remove' })}
        </button>
      </div>
    </Sheet>
  )
}

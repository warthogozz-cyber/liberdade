import { useState } from 'react'
import { useIntl } from 'react-intl'
import { Link } from 'react-router'
import { ApiError, ReauthCancelledError } from '@/shared/api'
import { useAccountDeletion } from '@/shared/api/privacy'
import { useReauth } from '@/shared/auth'
import { useOnline } from '@/shared/offline'
import { Sheet } from '@/shared/ui/Sheet'
import { dangerButton, outlineButton } from './buttons'
import './register'

/** Explains the 30 days, then schedules the account's deletion. A sole Owner is sent to the family first. */
export function DeleteAccountSheet({ onClose }: { onClose: () => void }) {
  const intl = useIntl()
  const online = useOnline()
  const run = useReauth()
  const { request } = useAccountDeletion()
  const [soleOwner, setSoleOwner] = useState(false)
  const [error, setError] = useState<string>()

  async function schedule() {
    if (!online || request.isPending) return
    setError(undefined)
    setSoleOwner(false)
    try {
      await run(() => request.mutateAsync())
    } catch (failure) {
      if (failure instanceof ReauthCancelledError) return
      if (failure instanceof ApiError && failure.code === 'account.sole-owner') {
        setSoleOwner(true)
        return
      }
      setError(
        (failure instanceof ApiError && failure.detail) ||
          intl.formatMessage({ id: 'privacy.deleteFailed' }),
      )
    }
    // On success `me` now carries the date, and the session layout shows the pending screen.
  }

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      title={intl.formatMessage({ id: 'privacy.deleteAccount.title' })}
    >
      <div className="flex flex-col gap-4">
        <p>{intl.formatMessage({ id: 'privacy.deleteAccount.body' })}</p>
        {soleOwner && (
          <div role="alert" className="flex flex-col gap-2 text-negative">
            <p>{intl.formatMessage({ id: 'privacy.soleOwner' })}</p>
            <Link to="/mais/familia" className={outlineButton}>
              {intl.formatMessage({ id: 'privacy.soleOwner.link' })}
            </Link>
          </div>
        )}
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
          disabled={!online || request.isPending}
          onClick={() => void schedule()}
          className={dangerButton}
        >
          {intl.formatMessage({ id: 'privacy.deleteAccount.confirm' })}
        </button>
      </div>
    </Sheet>
  )
}

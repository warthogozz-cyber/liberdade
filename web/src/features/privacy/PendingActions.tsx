import { useState } from 'react'
import { useIntl } from 'react-intl'
import { ApiError, ReauthCancelledError } from '@/shared/api'
import { useAccountDeletion, useFamilyDeletion } from '@/shared/api/privacy'
import { useReauth } from '@/shared/auth'
import { useOnline } from '@/shared/offline'
import { primaryButton } from './buttons'
import { ExportButton } from './ExportButtons'
import './register'

/**
 * What a pending deletion leaves the person: cancel it, or download the data. Pass a family's id for
 * the family's deletion; without it, the account's.
 */
export function PendingActions({ workspaceId }: { workspaceId?: string }) {
  const intl = useIntl()
  const online = useOnline()
  const run = useReauth()
  const account = useAccountDeletion()
  const family = useFamilyDeletion(workspaceId ?? '')
  const cancel = workspaceId === undefined ? account.cancel : family.cancel
  const [error, setError] = useState<string>()

  async function cancelDeletion() {
    setError(undefined)
    try {
      await run(() => cancel.mutateAsync())
    } catch (failure) {
      if (failure instanceof ReauthCancelledError) return
      setError(
        (failure instanceof ApiError && failure.detail) ||
          intl.formatMessage({ id: 'privacy.cancelFailed' }),
      )
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <button
        type="button"
        disabled={!online || cancel.isPending}
        onClick={() => void cancelDeletion()}
        className={primaryButton}
      >
        {intl.formatMessage({ id: 'privacy.cancelDeletion' })}
      </button>
      <ExportButton workspaceId={workspaceId} />
      {error && (
        <p role="alert" className="text-negative">
          {error}
        </p>
      )}
      {!online && <p className="text-text-muted">{intl.formatMessage({ id: 'privacy.offline' })}</p>}
    </div>
  )
}

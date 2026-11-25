import { useState } from 'react'
import { useIntl } from 'react-intl'
import { useNavigate } from 'react-router'
import { ApiError, ReauthCancelledError, useMe } from '@/shared/api'
import { useAccountDeletion, useFamilyDeletion } from '@/shared/api/privacy'
import { useReauth } from '@/shared/auth'
import { useOnline } from '@/shared/offline'
import { sendPendingNow } from '@/shared/offline/outbox'
import { primaryButton } from './buttons'
import { ExportButton } from './ExportButtons'
import './register'

interface ViewProps {
  workspaceId?: string
  cancel: { mutateAsync: () => Promise<unknown>; isPending: boolean }
  /** Runs once the cancel went through and `me` was refreshed. */
  onCancelled?: () => Promise<void> | void
}

function PendingActionsView({ workspaceId, cancel, onCancelled }: ViewProps) {
  const intl = useIntl()
  const online = useOnline()
  const run = useReauth()
  const [error, setError] = useState<string>()

  async function cancelDeletion() {
    setError(undefined)
    try {
      await run(() => cancel.mutateAsync())
      await onCancelled?.()
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
      {!online && (
        <p className="text-text-muted">{intl.formatMessage({ id: 'privacy.offline' })}</p>
      )}
    </div>
  )
}

function AccountPendingActions() {
  const navigate = useNavigate()
  const userId = useMe().data?.user.id
  const { cancel } = useAccountDeletion()
  return (
    <PendingActionsView
      cancel={cancel}
      onCancelled={async () => {
        // Expenses queued during the wait go out now, not after their backoff.
        if (userId) await sendPendingNow(userId)
        void navigate('/inicio', { replace: true })
      }}
    />
  )
}

function FamilyPendingActions({ workspaceId }: { workspaceId: string }) {
  const { cancel } = useFamilyDeletion(workspaceId)
  return <PendingActionsView workspaceId={workspaceId} cancel={cancel} />
}

/**
 * What a pending deletion leaves the person: cancel it, or download the data. Pass a family's id for
 * the family's deletion; without it, the account's.
 */
export function PendingActions({ workspaceId }: { workspaceId?: string }) {
  return workspaceId === undefined ? (
    <AccountPendingActions />
  ) : (
    <FamilyPendingActions workspaceId={workspaceId} />
  )
}

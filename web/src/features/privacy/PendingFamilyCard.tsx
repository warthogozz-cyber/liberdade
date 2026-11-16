import { useIntl } from 'react-intl'
import { SignOutButton } from '@/features/auth/SignOutButton'
import { WorkspaceSwitcher } from '@/features/members/WorkspaceSwitcher'
import type { MeWorkspace } from '@/shared/api'
import { useDocumentTitle } from '@/shared/ui'
import { PendingActions } from './PendingActions'
import './register'

/**
 * Stands in for the family's screens while its deletion is pending. The Owner can cancel it or
 * download the family's data; anyone else has no way in (the API hides the family from them).
 */
export function PendingFamilyCard({ workspace }: { workspace: MeWorkspace }) {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'privacy.title' }))
  return (
    <div className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">
        {workspace.deletionScheduledFor &&
          intl.formatMessage(
            { id: 'privacy.pendingFamily' },
            { date: intl.formatDate(workspace.deletionScheduledFor, { dateStyle: 'long' }) },
          )}
      </h1>
      <p className="text-text-muted">{intl.formatMessage({ id: 'privacy.pendingFamilyHint' })}</p>
      {workspace.role === 'OWNER' && <PendingActions workspaceId={workspace.id} />}
      <WorkspaceSwitcher />
      <SignOutButton />
    </div>
  )
}

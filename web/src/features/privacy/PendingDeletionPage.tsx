import { useIntl } from 'react-intl'
import { SignOutButton } from '@/features/auth/SignOutButton'
import { useMe } from '@/shared/api'
import { useDocumentTitle } from '@/shared/ui'
import { PendingActions } from './PendingActions'
import './register'

/** Replaces every screen while the person's account waits to be deleted: cancel it, export, or sign out. */
export function PendingDeletionPage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'privacy.title' }))
  const scheduled = useMe().data?.user.deletionScheduledFor
  return (
    <main className="mx-auto flex min-h-screen max-w-lg flex-col justify-center gap-4 p-6">
      <h1 className="text-2xl font-semibold">
        {scheduled &&
          intl.formatMessage(
            { id: 'privacy.pending' },
            { date: intl.formatDate(scheduled, { dateStyle: 'long' }) },
          )}
      </h1>
      <p className="text-text-muted">{intl.formatMessage({ id: 'privacy.pendingHint' })}</p>
      <PendingActions />
      <SignOutButton />
    </main>
  )
}

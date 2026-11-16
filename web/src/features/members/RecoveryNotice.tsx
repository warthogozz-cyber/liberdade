import { useState } from 'react'
import { useIntl } from 'react-intl'
import type { MeWorkspace } from '@/shared/api'
import { recoveryNoticeKey } from './recoveryWindow'
import './register'

function wasDismissed(membershipId: string, recoveredAt: string): boolean {
  try {
    return localStorage.getItem(recoveryNoticeKey(membershipId)) === recoveredAt
  } catch {
    return false
  }
}

/** Tells the person, for a week, that their access was recovered, so a stranger's recovery shows. */
export function RecoveryNotice({ workspace }: { workspace: MeWorkspace }) {
  const intl = useIntl()
  const { recoveredAt, membershipId } = workspace
  const [dismissed, setDismissed] = useState(
    () => recoveredAt === null || wasDismissed(membershipId, recoveredAt),
  )
  if (recoveredAt === null || dismissed) return null

  function dismiss() {
    try {
      localStorage.setItem(recoveryNoticeKey(membershipId), recoveredAt as string)
    } catch {
      // Without storage the notice shows again next time, which is the safe side.
    }
    setDismissed(true)
  }

  return (
    <section role="status" className="flex flex-col gap-2 rounded-card bg-surface p-4">
      <p>
        {intl.formatMessage(
          { id: 'recovery.notice' },
          {
            date: intl.formatDate(recoveredAt, { dateStyle: 'long', timeZone: workspace.timezone }),
          },
        )}
      </p>
      <button
        type="button"
        onClick={dismiss}
        className="min-h-12 self-start rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        {intl.formatMessage({ id: 'recovery.dismiss' })}
      </button>
    </section>
  )
}

import { FormattedMessage } from 'react-intl'
import { Link } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'
import { registerMessages } from '@/shared/i18n'
import { planRequiredMessages } from './planRequired.messages.pt-BR'
import { useDocumentTitle } from './useDocumentTitle'

registerMessages(planRequiredMessages)

const link =
  'flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/**
 * Stands in for a screen of the Family plan when the family is on the free plan: it says so in one
 * line and points to the Plan screen, with no countdown, badge or nudge.
 */
export function PlanRequired({ title }: { title: string }) {
  useDocumentTitle(title)
  const workspace = useCurrentWorkspace()
  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      <p>
        <FormattedMessage id="planRequired.text" />
      </p>
      {workspace !== undefined && workspace.role !== 'OWNER' && (
        <p className="text-text-muted">
          <FormattedMessage id="planRequired.ownerOnly" />
        </p>
      )}
      <Link to="/mais/plano" className={link}>
        <FormattedMessage id="planRequired.link" />
      </Link>
    </main>
  )
}

import { FormattedMessage, useIntl } from 'react-intl'
import { useDocumentTitle } from '@/shared/ui'
import './register'

/** Placeholder until its own task builds the screen. */
export function ReservePage() {
  useDocumentTitle(useIntl().formatMessage({ id: 'planning.reserve.title' }))
  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="planning.reserve.title" />
      </h1>
    </main>
  )
}

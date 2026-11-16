import { FormattedMessage, useIntl } from 'react-intl'
import { useDocumentTitle } from '@/shared/ui'

/** The new expense, full screen. Task 11 fills it. */
export function NewExpensePage() {
  useDocumentTitle(useIntl().formatMessage({ id: 'nav.newExpense' }))
  return (
    <main className="p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="nav.newExpense" />
      </h1>
    </main>
  )
}

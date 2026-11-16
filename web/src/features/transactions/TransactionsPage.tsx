import { FormattedMessage, useIntl } from 'react-intl'
import { useDocumentTitle } from '@/shared/ui'

/** The month's transactions. Task 11 fills it. */
export function TransactionsPage() {
  useDocumentTitle(useIntl().formatMessage({ id: 'nav.transactions' }))
  return (
    <main className="p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="nav.transactions" />
      </h1>
    </main>
  )
}

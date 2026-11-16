import { FormattedMessage, useIntl } from 'react-intl'
import { useDocumentTitle } from '@/shared/ui'

/** The home screen. Task 11 fills it. */
export function HomePage() {
  useDocumentTitle(useIntl().formatMessage({ id: 'nav.home' }))
  return (
    <main className="p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="nav.home" />
      </h1>
    </main>
  )
}

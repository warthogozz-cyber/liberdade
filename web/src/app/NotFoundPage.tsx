import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import { useDocumentTitle } from '@/shared/ui'

export function NotFoundPage() {
  useDocumentTitle(useIntl().formatMessage({ id: 'notFound.pageTitle' }))
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 p-6 text-center">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="notFound.title" />
      </h1>
      <Link
        to="/"
        className="rounded-field text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <FormattedMessage id="notFound.back" />
      </Link>
    </main>
  )
}

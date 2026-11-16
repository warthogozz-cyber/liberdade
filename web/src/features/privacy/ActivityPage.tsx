import { useIntl } from 'react-intl'
import { Link } from 'react-router'
import { useActivity } from '@/shared/api/privacy'
import { useDocumentTitle } from '@/shared/ui'
import { activityPhrase } from './activityPhrase'
import './register'

/** What happened on the person's account, newest first: the last 12 months, up to 100 entries. */
export function ActivityPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'activity.title' })
  useDocumentTitle(title)
  const activity = useActivity()

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      <p className="text-text-muted">{intl.formatMessage({ id: 'activity.hint' })}</p>
      {activity.isPending && <p>{intl.formatMessage({ id: 'common.loading' })}</p>}
      {activity.isError && <p role="alert">{intl.formatMessage({ id: 'activity.loadFailed' })}</p>}
      {activity.data?.length === 0 && (
        <p className="text-text-muted">{intl.formatMessage({ id: 'activity.empty' })}</p>
      )}
      {activity.data && activity.data.length > 0 && (
        <ul className="flex flex-col divide-y divide-border">
          {activity.data.map((item) => {
            const { browser, os } = item.metadata
            return (
              <li key={item.id} className="flex flex-col py-3">
                <span>{activityPhrase(intl, item)}</span>
                <span className="text-sm text-text-muted">
                  {item.byMe
                    ? intl.formatMessage({ id: 'activity.byMe' })
                    : intl.formatMessage(
                        { id: 'activity.by' },
                        {
                          name:
                            item.actorName ?? intl.formatMessage({ id: 'activity.formerMember' }),
                        },
                      )}
                </span>
                {browser && os && (
                  <span className="text-sm text-text-muted">
                    {intl.formatMessage({ id: 'activity.device' }, { browser, os })}
                  </span>
                )}
                <span className="text-sm text-text-muted">
                  {intl.formatDate(item.occurredAt, { dateStyle: 'medium', timeStyle: 'short' })}
                </span>
              </li>
            )
          })}
        </ul>
      )}
      <Link
        to="/mais/privacidade"
        className="flex min-h-12 items-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        {intl.formatMessage({ id: 'activity.back' })}
      </Link>
    </main>
  )
}

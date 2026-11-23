import { useId, type ReactNode } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { Link } from 'react-router'
import {
  useFormatMoney,
  type AccountView,
  type CategoryView,
  type MeWorkspace,
  type TransactionView,
} from '@/shared/api'
import { localDate } from '@/shared/i18n'
import { parseCents } from '@/shared/money'
import { useOnline } from '@/shared/offline'
import { canActOnPlanned, categoryLabel, transferRoute } from './listItems'
import './more.register'
import { usePlannedActions } from './usePlannedActions'

interface PlannedSectionProps {
  workspace: MeWorkspace
  /** The month's planned transactions, by `scheduledOn`. */
  planned: TransactionView[]
  /** The month of the list, which the row's sheet goes back to. */
  month: string
  categories: Map<string, CategoryView>
  accounts: Map<string, Pick<AccountView, 'name'>>
}

const focusRing =
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/**
 * What the family planned and has not confirmed yet, above the month's days. These never count in
 * the days or the totals: they have not happened. Confirmar and Pular need the connection.
 */
export function PlannedSection({
  workspace,
  planned,
  month,
  categories,
  accounts,
}: PlannedSectionProps) {
  const intl = useIntl()
  const online = useOnline()
  const headingId = useId()
  const { confirmPlanned, busy } = usePlannedActions(workspace)
  if (planned.length === 0) return null

  const today = localDate(new Date(), workspace.timezone)
  // Viewers see what is planned; they confirm and skip nothing. A youth acts only on a planned one
  // whose accounts are all theirs: one that comes from an adult's account is for looking.
  const canAct = (item: TransactionView) => canActOnPlanned(workspace.role, item, accounts)
  const shortDate = (date: string) =>
    intl.formatDate(`${date}T00:00:00Z`, { day: '2-digit', month: '2-digit', timeZone: 'UTC' })

  return (
    <section aria-labelledby={headingId} className="rounded-card bg-surface p-4">
      <h2 id={headingId} className="text-sm font-semibold text-text-muted">
        <FormattedMessage id="planned.title" />
      </h2>
      {planned.some(canAct) && !online && (
        <p className="mt-1 text-sm text-text-muted">
          <FormattedMessage id="transactions.offlineEditing" />
        </p>
      )}
      <ul className="divide-y divide-text-muted/20">
        {planned.map((item) => {
          const scheduledOn = item.scheduledOn ?? item.occurredOn
          const category = item.categoryId ? categories.get(item.categoryId) : undefined
          const name =
            item.description ||
            (item.kind === 'TRANSFER'
              ? transferRoute(item, accounts)
              : category
                ? categoryLabel(category)
                : intl.formatMessage({ id: 'transactions.uncategorized' }))
          return (
            <li key={item.id} className="flex items-center justify-between gap-3 py-2">
              <PlannedSummary
                item={item}
                name={name}
                note={
                  scheduledOn < today ? (
                    <FormattedMessage
                      id="planned.overdue"
                      values={{ date: shortDate(scheduledOn) }}
                    />
                  ) : (
                    shortDate(scheduledOn)
                  )
                }
                to={canAct(item) ? `/lancar?editar=${item.id}&mes=${month}` : undefined}
              />
              {canAct(item) && (
                <button
                  type="button"
                  disabled={!online || busy}
                  aria-label={intl.formatMessage({ id: 'planned.confirmNamed' }, { name })}
                  onClick={() => void confirmPlanned(item)}
                  className={`min-h-12 shrink-0 rounded-field border border-primary px-4 font-medium text-primary disabled:opacity-50 ${focusRing}`}
                >
                  <FormattedMessage id="planned.confirm" />
                </button>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}

interface PlannedSummaryProps {
  item: TransactionView
  name: string
  note: ReactNode
  /** Where tapping the row goes; none for a person who only looks. */
  to: string | undefined
}

function PlannedSummary({ item, name, note, to }: PlannedSummaryProps) {
  const format = useFormatMoney()
  const cents = parseCents(item.amount.amount)
  const amount =
    item.kind === 'INCOME'
      ? `+${format(cents)}`
      : item.kind === 'TRANSFER'
        ? format(cents)
        : format(-cents)
  const body = (
    <>
      <span className="flex w-full items-baseline justify-between gap-4">
        <span className="font-medium">{name}</span>
        <span className="font-semibold tabular-nums">{amount}</span>
      </span>
      <span className="text-sm text-text-muted">{note}</span>
    </>
  )
  const layout =
    'flex min-h-12 min-w-0 flex-1 flex-col justify-center gap-0.5 rounded-field text-left'
  return to ? (
    <Link to={to} className={`${layout} ${focusRing}`}>
      {body}
    </Link>
  ) : (
    <div className={layout}>{body}</div>
  )
}

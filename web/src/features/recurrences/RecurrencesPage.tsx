import { useMemo, useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import {
  ApiError,
  useAccounts,
  useCategories,
  useCurrentWorkspace,
  useFormatMoney,
  type MeWorkspace,
  type RecurringRuleView,
} from '@/shared/api'
import { useRules } from '@/shared/api/recurrences'
import { parseCents } from '@/shared/money'
import { useOnline } from '@/shared/offline'
import { useDocumentTitle } from '@/shared/ui'
import { RuleSheet } from './RuleSheet'
import { ruleText } from './ruleText'
import './register'
import { PageTip } from '@/shared/help'

type View = { kind: 'new' } | { kind: 'edit'; rule: RecurringRuleView }

const focusRing =
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** The family's recurring rules: Owner and Adult add, edit and archive; the others only look. */
export function RecurrencesPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'recurrences.title' })
  useDocumentTitle(title)
  const workspace = useCurrentWorkspace()

  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      <PageTip id="recurrences" />
      {workspace ? (
        <RecurrencesScreen workspace={workspace} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

function RecurrencesScreen({ workspace }: { workspace: MeWorkspace }) {
  const intl = useIntl()
  const online = useOnline()
  const rules = useRules(workspace.id)
  const [view, setView] = useState<View>()
  const canManage = workspace.role === 'OWNER' || workspace.role === 'ADULT'

  return (
    <>
      {canManage && (
        <>
          <button
            type="button"
            disabled={!online}
            onClick={() => setView({ kind: 'new' })}
            className={`min-h-12 rounded-field bg-primary px-4 font-medium text-on-primary disabled:opacity-50 ${focusRing}`}
          >
            {intl.formatMessage({ id: 'recurrences.new' })}
          </button>
          {!online && (
            <p className="text-sm text-text-muted">
              <FormattedMessage id="transactions.offlineEditing" />
            </p>
          )}
        </>
      )}
      {rules.data ? (
        rules.data.length === 0 ? (
          <p className="text-text-muted">
            <FormattedMessage id="recurrences.empty" />
          </p>
        ) : (
          <ul className="divide-y divide-text-muted/20">
            {rules.data.map((rule) => (
              <RuleRow
                key={rule.id}
                workspace={workspace}
                rule={rule}
                onOpen={canManage ? () => setView({ kind: 'edit', rule }) : undefined}
              />
            ))}
          </ul>
        )
      ) : rules.isError ? (
        <div className="flex flex-col items-start gap-2">
          <p>
            {(rules.error instanceof ApiError && rules.error.detail) || (
              <FormattedMessage id="common.error" />
            )}
          </p>
          <button
            type="button"
            onClick={() => void rules.refetch()}
            className={`min-h-12 rounded-field border border-primary px-4 font-medium text-primary ${focusRing}`}
          >
            <FormattedMessage id="common.retry" />
          </button>
        </div>
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
      {view && (
        <RuleSheet
          key={view.kind === 'edit' ? view.rule.id : 'new'}
          workspace={workspace}
          rule={view.kind === 'edit' ? view.rule : undefined}
          onClose={() => setView(undefined)}
        />
      )}
    </>
  )
}

interface RuleRowProps {
  workspace: MeWorkspace
  rule: RecurringRuleView
  /** Opens the rule for editing; none for a person who only looks. */
  onOpen: (() => void) | undefined
}

function RuleRow({ workspace, rule, onOpen }: RuleRowProps) {
  const intl = useIntl()
  const format = useFormatMoney()
  const categories = [
    ...(useCategories(workspace.id, 'EXPENSE').data ?? []),
    ...(useCategories(workspace.id, 'INCOME').data ?? []),
  ]
  const accounts = useAccounts(workspace.id).data
  const category = categories.find(({ id }) => id === rule.categoryId)
  const transfer = rule.kind === 'TRANSFER'
  const name = useMemo(
    () =>
      rule.description ||
      (transfer
        ? [rule.accountId, rule.toAccountId]
            .map((id) => accounts?.find((each) => each.id === id)?.name ?? '')
            .join(' → ')
        : (category?.name ?? intl.formatMessage({ id: 'transactions.uncategorized' }))),
    [rule, transfer, category, accounts, intl],
  )
  const cents = parseCents(rule.amount.amount)
  const amount =
    rule.kind === 'INCOME' ? `+${format(cents)}` : transfer ? format(cents) : format(-cents)
  const next = rule.nextDueOn
    ? intl.formatMessage(
        { id: 'recurrences.next' },
        {
          date: intl.formatDate(`${rule.nextDueOn}T00:00:00Z`, {
            day: '2-digit',
            month: '2-digit',
            timeZone: 'UTC',
          }),
        },
      )
    : intl.formatMessage({ id: 'recurrences.ended' })
  const body = (
    <>
      <span className="flex w-full items-baseline justify-between gap-4">
        <span className="font-medium">{name}</span>
        <span className="font-semibold tabular-nums">{amount}</span>
      </span>
      <span className="text-sm text-text-muted">{ruleText(rule, intl.locale)}</span>
      <span className="text-sm text-text-muted">{next}</span>
    </>
  )
  const layout = 'flex min-h-12 w-full flex-col gap-0.5 py-3 text-left'
  return (
    <li>
      {onOpen ? (
        <button type="button" onClick={onOpen} className={`${layout} rounded-field ${focusRing}`}>
          {body}
        </button>
      ) : (
        <div className={layout}>{body}</div>
      )}
    </li>
  )
}

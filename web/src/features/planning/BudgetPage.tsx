import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { useCategories, useCurrentWorkspace, type MeWorkspace } from '@/shared/api'
import { useBudgets, useCopyBudgets, type BudgetLineView } from '@/shared/api/planning'
import { addMonths, currentMonth, maxMonth } from '@/shared/i18n'
import { useOnline } from '@/shared/offline'
import { useDocumentTitle, useToast } from '@/shared/ui'
import { MonthSwitcher } from '@/shared/ui/MonthSwitcher'
import { BudgetLimitSheet } from './BudgetLimitSheet'
import { BudgetLine } from './BudgetLine'
import './register'

const action =
  'min-h-12 rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

/** The month's limits by category, with what each has spent (spec 6.5). */
export function BudgetPage() {
  useDocumentTitle(useIntl().formatMessage({ id: 'planning.budget.title' }))
  const workspace = useCurrentWorkspace()
  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">
        <FormattedMessage id="planning.budget.title" />
      </h1>
      {workspace ? (
        <BudgetContent workspace={workspace} />
      ) : (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      )}
    </main>
  )
}

type Opened = { kind: 'new' } | { kind: 'edit'; line: BudgetLineView }

function BudgetContent({ workspace }: { workspace: MeWorkspace }) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const now = new Date()
  const [month, setMonth] = useState(() => currentMonth(now, workspace.timezone))
  const [opened, setOpened] = useState<Opened>()
  const budgets = useBudgets(workspace.id, month)
  const previous = useBudgets(workspace.id, addMonths(month, -1))
  const categories = useCategories(workspace.id, 'EXPENSE')
  const copy = useCopyBudgets(workspace.id)
  const editable = workspace.role === 'OWNER' || workspace.role === 'ADULT'

  const all = categories.data ?? []
  const byId = new Map(all.map((each) => [each.id, each]))
  const lines = budgets.data?.lines ?? []
  const limited = new Set(lines.map((each) => each.categoryId))
  const options = all.filter((each) => each.archivedAt === null && !limited.has(each.id))
  const canCopy =
    editable && (previous.data?.lines ?? []).some((each) => !limited.has(each.categoryId))

  async function copyPrevious() {
    try {
      const result = await copy.mutateAsync(month)
      toast.show({
        message: intl.formatMessage(
          { id: result.copied === 0 ? 'planning.budget.copyNone' : 'planning.budget.copied' },
          { count: result.copied },
        ),
      })
    } catch (error) {
      console.error(error)
      toast.show({ message: intl.formatMessage({ id: 'planning.budget.failed' }) })
    }
  }

  const editing = opened?.kind === 'edit' ? byId.get(opened.line.categoryId) : undefined

  return (
    <>
      <MonthSwitcher month={month} onChange={setMonth} max={maxMonth(now, workspace.timezone)} />
      {budgets.isPending ? (
        <p>
          <FormattedMessage id="common.loading" />
        </p>
      ) : budgets.data === undefined ? (
        <p>
          <FormattedMessage id="planning.budget.loadFailed" />
        </p>
      ) : lines.length === 0 ? (
        <p className="rounded-card bg-surface p-4">
          <FormattedMessage id="planning.budget.empty" />
        </p>
      ) : (
        <ul className="flex flex-col gap-3">
          {lines.map((line) => (
            <li key={line.categoryId}>
              <BudgetLine
                name={byId.get(line.categoryId)?.name ?? ''}
                line={line}
                disabled={!online}
                {...(editable ? { onOpen: () => setOpened({ kind: 'edit', line }) } : {})}
              />
            </li>
          ))}
        </ul>
      )}
      {editable && (
        <div className="flex flex-col gap-3">
          <button
            type="button"
            disabled={!online}
            onClick={() => setOpened({ kind: 'new' })}
            className={`${action} bg-primary text-on-primary`}
          >
            <FormattedMessage id="planning.budget.define" />
          </button>
          {canCopy && (
            <button
              type="button"
              disabled={!online || copy.isPending}
              onClick={() => void copyPrevious()}
              className={action}
            >
              <FormattedMessage id="planning.budget.copy" />
            </button>
          )}
          {!online && (
            <p className="text-sm text-text-muted">
              <FormattedMessage id="planning.budget.offline" />
            </p>
          )}
        </div>
      )}
      {opened && (opened.kind === 'new' || editing) && (
        <BudgetLimitSheet
          workspace={workspace}
          month={month}
          options={options}
          {...(opened.kind === 'edit' && editing
            ? { editing: { category: editing, line: opened.line } }
            : {})}
          onClose={() => setOpened(undefined)}
          onConflict={() => void budgets.refetch()}
        />
      )}
    </>
  )
}

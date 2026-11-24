import { useState } from 'react'
import { useIntl } from 'react-intl'
import { ApiError, type CategoryView, type MeWorkspace } from '@/shared/api'
import { useDeleteBudget, useSetBudget, type BudgetLineView } from '@/shared/api/planning'
import { centsToApi, parseCents, parseTypedAmount, typedAmount } from '@/shared/money'
import { useOnline } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { Sheet } from '@/shared/ui/Sheet'
import './register'

interface BudgetLimitSheetProps {
  workspace: MeWorkspace
  month: string
  /** A limit being changed; without it, a new limit on one of `options`. */
  editing?: { category: CategoryView; line: BudgetLineView }
  /** The expense categories with no limit this month. */
  options: readonly CategoryView[]
  onClose: () => void
  /** The limit changed under the person (409): the screen reloads it. */
  onConflict: () => void
}

const field =
  'min-h-12 w-full rounded-field border border-primary bg-surface px-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const button =
  'min-h-12 rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

/** Sets, changes or removes one category's limit for a month. All of it needs the connection. */
export function BudgetLimitSheet({
  workspace,
  month,
  editing,
  options,
  onClose,
  onConflict,
}: BudgetLimitSheetProps) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const set = useSetBudget(workspace.id)
  const remove = useDeleteBudget(workspace.id)
  const [categoryId, setCategoryId] = useState(editing?.category.id ?? '')
  const [text, setText] = useState(
    editing ? typedAmount(parseCents(editing.line.limit.amount)) : '',
  )
  const [error, setError] = useState<string>()
  const busy = set.isPending || remove.isPending

  function failed(failure: unknown) {
    if (failure instanceof ApiError && failure.status === 409) {
      onClose()
      onConflict()
      toast.show({ message: intl.formatMessage({ id: 'planning.budget.conflict' }) })
      return
    }
    console.error(failure)
    setError(intl.formatMessage({ id: 'planning.budget.failed' }))
  }

  async function save() {
    if (!online || busy) return
    const cents = parseTypedAmount(text)
    if (cents === undefined || categoryId === '') {
      setError(intl.formatMessage({ id: 'planning.budget.sheet.invalid' }))
      return
    }
    setError(undefined)
    try {
      await set.mutateAsync({
        month,
        categoryId,
        body: {
          limitAmount: { amount: centsToApi(cents), currency: workspace.currency },
          ...(editing ? { version: editing.line.version } : {}),
        },
      })
    } catch (failure) {
      failed(failure)
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'planning.budget.sheet.saved' }) })
    onClose()
  }

  async function removeLimit() {
    if (!online || busy || !editing) return
    setError(undefined)
    try {
      await remove.mutateAsync({ month, categoryId: editing.category.id })
    } catch (failure) {
      failed(failure)
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'planning.budget.sheet.removed' }) })
    onClose()
  }

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      title={
        editing
          ? intl.formatMessage(
              { id: 'planning.budget.sheet.edit' },
              { category: editing.category.name },
            )
          : intl.formatMessage({ id: 'planning.budget.sheet.new' })
      }
    >
      <div className="flex flex-col gap-4">
        {!editing &&
          (options.length === 0 ? (
            <p>{intl.formatMessage({ id: 'planning.budget.sheet.noCategories' })}</p>
          ) : (
            <label className="flex flex-col gap-1">
              <span className="font-medium">
                {intl.formatMessage({ id: 'planning.budget.sheet.category' })}
              </span>
              <select
                value={categoryId}
                onChange={(event) => setCategoryId(event.target.value)}
                className={field}
              >
                <option value="">
                  {intl.formatMessage({ id: 'planning.budget.sheet.choose' })}
                </option>
                {options.map((each) => (
                  <option key={each.id} value={each.id}>
                    {each.name}
                  </option>
                ))}
              </select>
            </label>
          ))}
        <label className="flex flex-col gap-1">
          <span className="font-medium">
            {intl.formatMessage({ id: 'planning.budget.sheet.limit' })}
          </span>
          <input
            inputMode="decimal"
            autoComplete="off"
            value={text}
            onChange={(event) => setText(event.target.value)}
            className={field}
          />
        </label>
        {error && (
          <p role="alert" className="text-negative">
            {error}
          </p>
        )}
        {!online && (
          <p className="text-text-muted">{intl.formatMessage({ id: 'planning.budget.offline' })}</p>
        )}
        <button
          type="button"
          disabled={!online || busy || (!editing && options.length === 0)}
          onClick={() => void save()}
          className={`${button} bg-primary text-on-primary`}
        >
          {intl.formatMessage({ id: 'planning.budget.sheet.save' })}
        </button>
        {editing && (
          <button
            type="button"
            disabled={!online || busy}
            onClick={() => void removeLimit()}
            className={`${button} border border-negative text-negative`}
          >
            {intl.formatMessage({ id: 'planning.budget.sheet.remove' })}
          </button>
        )}
      </div>
    </Sheet>
  )
}

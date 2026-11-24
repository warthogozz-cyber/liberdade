import { useIntl } from 'react-intl'
import { useFormatMoney } from '@/shared/api'
import type { BudgetLineView } from '@/shared/api/planning'
import { parseCents } from '@/shared/money'
import './register'

interface BudgetLineProps {
  name: string
  line: BudgetLineView
  /** Opens the limit; without it the line is only read (a Viewer). */
  onOpen?: () => void
  disabled?: boolean
}

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/**
 * One limit: the name, the bar, "R$ 960,00 de R$ 1.200,00" and the use in %. Amber only from 80%
 * and red only past 100%, and the words always say it too, so colour is never the one signal.
 */
export function BudgetLine({ name, line, onOpen, disabled }: BudgetLineProps) {
  const intl = useIntl()
  const format = useFormatMoney()
  const alert =
    line.state === 'OVER' && line.overBy
      ? intl.formatMessage(
          { id: 'planning.budget.over' },
          { amount: format(parseCents(line.overBy.amount)) },
        )
      : line.state === 'AT'
        ? intl.formatMessage({ id: 'planning.budget.at' })
        : line.state === 'NEAR'
          ? intl.formatMessage({ id: 'planning.budget.near' })
          : null
  const bar =
    line.state === 'OVER' ? 'bg-negative' : line.state === 'UNDER' ? 'bg-primary' : 'bg-amber-500'
  const text = line.state === 'OVER' ? 'text-negative' : 'text-text'
  const body = (
    <>
      <span className="flex items-baseline justify-between gap-4">
        <span className="truncate font-medium">{name}</span>
        <span className="tabular-nums text-text-muted">
          {intl.formatMessage({ id: 'planning.budget.usage' }, { percent: line.usagePercent })}
        </span>
      </span>
      <span
        role="meter"
        aria-label={name}
        aria-valuemin={0}
        aria-valuemax={Math.max(100, line.usagePercent)}
        aria-valuenow={line.usagePercent}
        className="block h-2 overflow-hidden rounded-full bg-text-muted/20"
      >
        <span
          className={`block h-full rounded-full ${bar}`}
          style={{ width: `${Math.min(100, line.usagePercent)}%` }}
        />
      </span>
      <span className="text-sm tabular-nums text-text-muted">
        {intl.formatMessage(
          { id: 'planning.budget.of' },
          {
            spent: format(parseCents(line.spent.amount)),
            limit: format(parseCents(line.limit.amount)),
          },
        )}
      </span>
      {alert && <span className={`text-sm font-medium ${text}`}>{alert}</span>}
    </>
  )
  const box = 'flex min-h-14 w-full flex-col gap-1 rounded-card bg-surface p-4 text-left'
  return onOpen ? (
    <button
      type="button"
      disabled={disabled}
      onClick={onOpen}
      className={`${box} ${focus} disabled:opacity-60`}
    >
      {body}
    </button>
  ) : (
    <div className={box}>{body}</div>
  )
}

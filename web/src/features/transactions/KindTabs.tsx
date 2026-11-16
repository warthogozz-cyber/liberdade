import { useRef, type KeyboardEvent } from 'react'
import { useIntl } from 'react-intl'
import type { TransactionKind } from '@/shared/api'
import { kindTabId } from './kindTabId'

export type { TransactionKind }

const KINDS = [
  ['EXPENSE', 'transaction.kind.expense'],
  ['INCOME', 'transaction.kind.income'],
  ['TRANSFER', 'transaction.kind.transfer'],
] as const satisfies readonly (readonly [TransactionKind, string])[]

interface KindTabsProps {
  value: TransactionKind
  onChange: (kind: TransactionKind) => void
  /** The id of the panel the tabs control. */
  panelId: string
}

const tabClass =
  'min-h-12 flex-1 rounded-field px-2 font-medium text-text-muted aria-selected:bg-primary aria-selected:text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/**
 * Gasto, Receita and Transferência as WAI-ARIA tabs with automatic activation: only the chosen tab
 * is reached by Tab, and the arrows (wrapping around), Home and End choose and focus another.
 */
export function KindTabs({ value, onChange, panelId }: KindTabsProps) {
  const intl = useIntl()
  const tabs = useRef<(HTMLButtonElement | null)[]>([])

  function choose(index: number) {
    const wrapped = (index + KINDS.length) % KINDS.length
    onChange(KINDS[wrapped][0])
    tabs.current[wrapped]?.focus()
  }

  function onKeyDown(event: KeyboardEvent, index: number) {
    const targets: Partial<Record<string, number>> = {
      ArrowRight: index + 1,
      ArrowLeft: index - 1,
      Home: 0,
      End: KINDS.length - 1,
    }
    const target = targets[event.key]
    if (target === undefined) return
    event.preventDefault()
    choose(target)
  }

  return (
    <div
      role="tablist"
      aria-label={intl.formatMessage({ id: 'transaction.kind.label' })}
      className="flex gap-1 rounded-field bg-surface p-1"
    >
      {KINDS.map(([kind, label], index) => (
        <button
          key={kind}
          ref={(element) => {
            tabs.current[index] = element
          }}
          type="button"
          role="tab"
          id={kindTabId(panelId, kind)}
          aria-selected={kind === value}
          aria-controls={panelId}
          tabIndex={kind === value ? 0 : -1}
          onClick={() => onChange(kind)}
          onKeyDown={(event) => onKeyDown(event, index)}
          className={tabClass}
        >
          {intl.formatMessage({ id: label })}
        </button>
      ))}
    </div>
  )
}

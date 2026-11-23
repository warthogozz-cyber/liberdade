import { useRef, type KeyboardEvent } from 'react'
import { useIntl } from 'react-intl'
import { categoryTabId, type CategoryTab } from './categoryStyle'
import './register'

const KINDS = [
  ['EXPENSE', 'categories.tab.expense'],
  ['INCOME', 'categories.tab.income'],
  ['TAGS', 'categories.tab.tags'],
] as const satisfies readonly (readonly [CategoryTab, FormatjsIntl.Message['ids']])[]

interface CategoryTabsProps {
  value: CategoryTab
  onChange: (tab: CategoryTab) => void
  /** The id of the panel the tabs control. */
  panelId: string
}

const tabClass =
  'min-h-12 flex-1 rounded-field px-2 font-medium text-text-muted aria-selected:bg-primary aria-selected:text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/**
 * Gastos, Receitas and Tags as WAI-ARIA tabs with automatic activation, as the kind tabs of Lançar: only
 * the chosen tab is reached by Tab, and the arrows (wrapping around), Home and End choose and
 * focus another.
 */
export function CategoryTabs({ value, onChange, panelId }: CategoryTabsProps) {
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
      aria-label={intl.formatMessage({ id: 'categories.tabs.label' })}
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
          id={categoryTabId(panelId, kind)}
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

import { memo, useCallback, useId, useState, type Dispatch, type SetStateAction } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import type { CategoryView } from '@/shared/api'
import { useFormatMoney } from '@/shared/api/useFormatMoney'
import { CategoryPicker } from '@/features/transactions/CategoryPicker'
import type { importMessages } from './messages.pt-BR'
import type { PreviewLine, PreviewState } from './preview'
import './register'

const button =
  'flex min-h-12 items-center justify-center rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

/** The states that carry a badge; a bill payment carries its notice instead. */
const BADGES: Partial<Record<PreviewState, keyof typeof importMessages>> = {
  alreadyImported: 'import.state.alreadyImported',
  confirmPlanned: 'import.state.confirmPlanned',
  possibleRepeat: 'import.state.possibleRepeat',
}

interface ImportPreviewProps {
  /** The family's categories of each kind. */
  expense: CategoryView[]
  income: CategoryView[]
  lines: PreviewLine[]
  setLines: Dispatch<SetStateAction<PreviewLine[]>>
  online: boolean
  sending: boolean
  onImport: () => void
}

/**
 * The file's lines before anything is sent: a labelled checkbox each, the state as text, the category
 * and, on a card, the bill-payment notice. The button stays off with nothing checked, with a checked
 * line still without a category, and offline.
 */
export function ImportPreview({
  expense,
  income,
  lines,
  setLines,
  online,
  sending,
  onImport,
}: ImportPreviewProps) {
  const titleId = useId()
  // The bulk offer shows under the line whose category was picked last, never under every line.
  const [lastPicked, setLastPicked] = useState<string | null>(null)

  const checked = lines.filter((line) => line.checked)
  const missing = checked.filter((line) => line.categoryId === null).length
  const disabled = checked.length === 0 || missing > 0 || !online || sending
  const picked = lines.find((line) => line.id === lastPicked)
  const bulkCount =
    picked && picked.checked && picked.categoryId !== null
      ? lines.filter((other) => isBulkTarget(other, picked)).length
      : 0

  const toggle = useCallback(
    (id: string, on: boolean) =>
      setLines((all) => all.map((line) => (line.id === id ? { ...line, checked: on } : line))),
    [setLines],
  )
  const pick = useCallback(
    (id: string, categoryId: string) => {
      setLines((all) => all.map((line) => (line.id === id ? { ...line, categoryId } : line)))
      setLastPicked(id)
    },
    [setLines],
  )
  const fillTheRest = useCallback(
    (id: string) => {
      setLines((all) => {
        const source = all.find((line) => line.id === id)
        if (!source) return all
        return all.map((line) =>
          isBulkTarget(line, source) ? { ...line, categoryId: source.categoryId } : line,
        )
      })
      setLastPicked(null)
    },
    [setLines],
  )

  return (
    <section className="flex flex-col gap-4">
      <h2 id={titleId} className="text-xl font-semibold">
        <FormattedMessage id="import.preview.title" />
      </h2>
      <p>
        <FormattedMessage id="import.preview.intro" />
      </p>
      <ul aria-labelledby={titleId} className="flex flex-col gap-3">
        {lines.map((line) => (
          <PreviewRow
            key={line.id}
            line={line}
            categories={line.kind === 'EXPENSE' ? expense : income}
            bulkCount={line.id === lastPicked ? bulkCount : 0}
            onToggle={toggle}
            onPick={pick}
            onFillTheRest={fillTheRest}
          />
        ))}
      </ul>
      <div className="flex flex-col gap-2">
        {!online && (
          <p role="status" className="rounded-card bg-surface p-4">
            <FormattedMessage id="import.offline" />
          </p>
        )}
        {online && missing > 0 && (
          <p className="text-text-muted">
            <FormattedMessage id="import.needsCategory" values={{ n: missing }} />
          </p>
        )}
        <button
          type="button"
          disabled={disabled}
          onClick={onImport}
          className={`${button} bg-primary text-on-primary`}
        >
          <FormattedMessage id="import.submit" values={{ n: checked.length }} />
        </button>
        {sending && (
          <p role="status">
            <FormattedMessage id="import.sending" />
          </p>
        )}
      </div>
    </section>
  )
}

/** Another checked line of the same kind still without a category. */
function isBulkTarget(line: PreviewLine, source: PreviewLine): boolean {
  return (
    line.id !== source.id && line.checked && line.categoryId === null && line.kind === source.kind
  )
}

interface PreviewRowProps {
  line: PreviewLine
  categories: CategoryView[]
  bulkCount: number
  onToggle: (id: string, on: boolean) => void
  onPick: (id: string, categoryId: string) => void
  onFillTheRest: (id: string) => void
}

const PreviewRow = memo(function PreviewRow({
  line,
  categories,
  bulkCount,
  onToggle,
  onPick,
  onFillTheRest,
}: PreviewRowProps) {
  const intl = useIntl()
  const format = useFormatMoney()
  const date = intl.formatDate(`${line.occurredOn}T00:00:00Z`, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'UTC',
  })
  const amount = line.kind === 'INCOME' ? `+${format(line.absCents)}` : format(-line.absCents)
  const badge = BADGES[line.state]

  return (
    <li className="flex flex-col gap-3 rounded-card bg-surface p-4">
      <div className="flex flex-wrap items-start gap-3">
        <label className="flex min-h-12 flex-1 cursor-pointer items-start gap-3">
          <input
            type="checkbox"
            checked={line.checked}
            onChange={(event) => onToggle(line.id, event.target.checked)}
            className="mt-1 size-5 shrink-0 accent-primary"
          />
          <span className="flex flex-1 flex-wrap gap-x-2">
            <span className="text-text-muted tabular-nums">{date}</span>{' '}
            <span className="flex-1 break-words">{line.description}</span>{' '}
            <span className="font-medium tabular-nums">{amount}</span>
          </span>
        </label>
        {badge && (
          <span className="rounded-full border border-text-muted/40 px-3 py-1 text-sm">
            {intl.formatMessage({ id: badge })}
          </span>
        )}
      </div>
      {line.billPayment && (
        <p className="text-sm text-text-muted">
          <FormattedMessage id="import.billPaymentNotice" />
        </p>
      )}
      {line.checked && (
        <CategoryPicker
          categories={categories}
          value={line.categoryId ?? undefined}
          onChange={(categoryId) => onPick(line.id, categoryId)}
          onSheetChange={ignoreSheet}
          name={`category-${line.id}`}
          legend={
            <>
              <span aria-hidden="true">
                <FormattedMessage id="expense.category" />
              </span>
              <span className="sr-only">
                <FormattedMessage
                  id="import.preview.categoryOf"
                  values={{ description: line.description }}
                />
              </span>
            </>
          }
        />
      )}
      {bulkCount > 0 && (
        <button
          type="button"
          onClick={() => onFillTheRest(line.id)}
          className={`${button} self-start border border-primary text-primary`}
        >
          <FormattedMessage id="import.bulk" values={{ n: bulkCount }} />
        </button>
      )}
    </li>
  )
})

/** The preview has no keypad, so a category sheet opening needs no reaction. */
function ignoreSheet() {}

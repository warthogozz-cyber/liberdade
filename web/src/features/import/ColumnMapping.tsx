import { useState } from 'react'
import { useIntl } from 'react-intl'
import { detectDecimal, type CsvMapping } from './csvDetect'
import type { importMessages } from './messages.pt-BR'
import './register'

const field = 'min-h-12 rounded-field border border-border bg-bg px-3'
const button =
  'flex min-h-12 items-center justify-center rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'
const SAMPLE_ROWS = 5
const NONE = ''

interface ColumnMappingProps {
  rows: string[][]
  initial: CsvMapping
  onConfirm: (mapping: CsvMapping) => void
  /** Set when the person came back from the preview to adjust a mapping that was already in use. */
  onCancel?: () => void
}

type AmountColumns = { split: boolean; value: string; debit: string; credit: string }

/**
 * Lets the person say which column is which, and how the cents are written, when the file's own header was not
 * enough or when they came back from the preview to adjust it.
 */
export function ColumnMapping({ rows, initial, onConfirm, onCancel }: ColumnMappingProps) {
  const intl = useIntl()
  const columns = rows.reduce((most, row) => Math.max(most, row.length), 0)
  const pick = (column: number) => (column >= 0 && column < columns ? String(column) : NONE)
  const [date, setDate] = useState(pick(initial.date))
  const [description, setDescription] = useState(pick(initial.description))
  const [amount, setAmount] = useState<AmountColumns>({
    split: initial.amount.kind === 'split',
    value: initial.amount.kind === 'signed' ? pick(initial.amount.column) : NONE,
    debit: initial.amount.kind === 'split' ? pick(initial.amount.debit) : NONE,
    credit: initial.amount.kind === 'split' ? pick(initial.amount.credit) : NONE,
  })
  const [decimal, setDecimal] = useState<CsvMapping['decimal']>(initial.decimal)
  const { split, value, debit, credit } = amount

  const header = initial.headerRow >= 0 ? rows[initial.headerRow] : undefined
  const sample = rows.slice(initial.headerRow + 1, initial.headerRow + 1 + SAMPLE_ROWS)
  const options = Array.from({ length: columns }, (_, column) => {
    const name = header?.[column]?.trim()
    return {
      column,
      label: name ? name : intl.formatMessage({ id: 'import.columns.column' }, { n: column + 1 }),
    }
  })
  const complete =
    date !== NONE &&
    description !== NONE &&
    (split ? debit !== NONE && credit !== NONE : value !== NONE)

  /** A new amount column brings the decimal its cells show; the person can still change it afterwards. */
  function changeAmount(change: Partial<AmountColumns>) {
    const next = { ...amount, ...change }
    setAmount(next)
    const chosen = next.split ? [next.debit, next.credit] : [next.value]
    if (chosen.every((column) => column !== NONE)) {
      setDecimal(detectDecimal(rows.slice(initial.headerRow + 1), chosen.map(Number)))
    }
  }

  function select(id: keyof typeof importMessages, current: string, set: (v: string) => void) {
    return (
      <label className="flex flex-col gap-1">
        <span className="font-medium">{intl.formatMessage({ id })}</span>
        <select value={current} onChange={(event) => set(event.target.value)} className={field}>
          <option value={NONE}>{intl.formatMessage({ id: 'import.columns.choose' })}</option>
          {options.map((option) => (
            <option key={option.column} value={String(option.column)}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
    )
  }

  function confirm() {
    if (!complete) return
    onConfirm({
      headerRow: initial.headerRow,
      date: Number(date),
      description: Number(description),
      amount: split
        ? { kind: 'split', debit: Number(debit), credit: Number(credit) }
        : { kind: 'signed', column: Number(value) },
      decimal,
    })
  }

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        confirm()
      }}
      className="flex flex-col gap-4"
    >
      <h2 className="text-lg font-semibold">
        {intl.formatMessage({ id: 'import.columns.title' })}
      </h2>
      <p className="text-text-muted">
        {intl.formatMessage({
          id: onCancel ? 'import.columns.introAdjust' : 'import.columns.intro',
        })}
      </p>
      {select('import.columns.date', date, setDate)}
      {select('import.columns.description', description, setDescription)}
      <label className="flex flex-col gap-1">
        <span className="font-medium">{intl.formatMessage({ id: 'import.columns.mode' })}</span>
        <select
          value={split ? 'split' : 'signed'}
          onChange={(event) => changeAmount({ split: event.target.value === 'split' })}
          className={field}
        >
          <option value="signed">{intl.formatMessage({ id: 'import.columns.modeSigned' })}</option>
          <option value="split">{intl.formatMessage({ id: 'import.columns.modeSplit' })}</option>
        </select>
      </label>
      {split ? (
        <>
          {select('import.columns.debit', debit, (v) => changeAmount({ debit: v }))}
          {select('import.columns.credit', credit, (v) => changeAmount({ credit: v }))}
        </>
      ) : (
        select('import.columns.amount', value, (v) => changeAmount({ value: v }))
      )}
      <label className="flex flex-col gap-1">
        <span className="font-medium">{intl.formatMessage({ id: 'import.columns.decimal' })}</span>
        <select
          value={decimal}
          onChange={(event) => setDecimal(event.target.value === '.' ? '.' : ',')}
          className={field}
        >
          <option value=",">{intl.formatMessage({ id: 'import.columns.decimalComma' })}</option>
          <option value=".">{intl.formatMessage({ id: 'import.columns.decimalDot' })}</option>
        </select>
      </label>
      <div
        role="region"
        tabIndex={0}
        aria-label={intl.formatMessage({ id: 'import.columns.sample' })}
        className="overflow-x-auto rounded-field border border-border focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <table className="w-full text-left text-sm">
          <caption className="sr-only">
            {intl.formatMessage({ id: 'import.columns.sample' })}
          </caption>
          {header && (
            <thead>
              <tr>
                {options.map((option) => (
                  <th key={option.column} scope="col" className="px-3 py-2 font-medium">
                    {option.label}
                  </th>
                ))}
              </tr>
            </thead>
          )}
          <tbody>
            {sample.map((row, i) => (
              <tr key={i} className="border-t border-border">
                {options.map((option) => (
                  <td key={option.column} className="px-3 py-2">
                    {row[option.column] ?? ''}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <button type="submit" disabled={!complete} className={`${button} bg-primary text-on-primary`}>
        {intl.formatMessage({ id: 'import.columns.continue' })}
      </button>
      {onCancel && (
        <button
          type="button"
          onClick={onCancel}
          className={`${button} border border-primary text-primary`}
        >
          {intl.formatMessage({ id: 'import.columns.back' })}
        </button>
      )}
    </form>
  )
}

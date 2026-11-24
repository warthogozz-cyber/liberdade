import { useState } from 'react'
import { useIntl } from 'react-intl'
import type { CsvMapping } from './csvDetect'
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
}

/** Lets the person say which column is which when the file's own header was not enough. */
export function ColumnMapping({ rows, initial, onConfirm }: ColumnMappingProps) {
  const intl = useIntl()
  const columns = rows.reduce((most, row) => Math.max(most, row.length), 0)
  const pick = (column: number) => (column >= 0 && column < columns ? String(column) : NONE)
  const [date, setDate] = useState(pick(initial.date))
  const [description, setDescription] = useState(pick(initial.description))
  const [split, setSplit] = useState(initial.amount.kind === 'split')
  const [value, setValue] = useState(
    initial.amount.kind === 'signed' ? pick(initial.amount.column) : NONE,
  )
  const [debit, setDebit] = useState(
    initial.amount.kind === 'split' ? pick(initial.amount.debit) : NONE,
  )
  const [credit, setCredit] = useState(
    initial.amount.kind === 'split' ? pick(initial.amount.credit) : NONE,
  )

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
      decimal: initial.decimal,
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
      <p className="text-text-muted">{intl.formatMessage({ id: 'import.columns.intro' })}</p>
      {select('import.columns.date', date, setDate)}
      {select('import.columns.description', description, setDescription)}
      <label className="flex flex-col gap-1">
        <span className="font-medium">{intl.formatMessage({ id: 'import.columns.mode' })}</span>
        <select
          value={split ? 'split' : 'signed'}
          onChange={(event) => setSplit(event.target.value === 'split')}
          className={field}
        >
          <option value="signed">{intl.formatMessage({ id: 'import.columns.modeSigned' })}</option>
          <option value="split">{intl.formatMessage({ id: 'import.columns.modeSplit' })}</option>
        </select>
      </label>
      {split ? (
        <>
          {select('import.columns.debit', debit, setDebit)}
          {select('import.columns.credit', credit, setCredit)}
        </>
      ) : (
        select('import.columns.amount', value, setValue)
      )}
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
    </form>
  )
}

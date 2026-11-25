import { useIntl } from 'react-intl'
import { useFormatMoney } from '@/shared/api'
import type { ActualVsPlanned } from '@/shared/api/ventures'
import { parseCents } from '@/shared/money'
import type { venturesMessages } from './messages.pt-BR'

interface Props {
  months: ActualVsPlanned['months']
  totals: ActualVsPlanned['totals']
}

const COLUMNS = ['income', 'expense', 'result', 'planned', 'difference'] as const

type Money = { amount: string } | null

interface RowValues {
  income: Money
  expense: Money
  result: Money
  planned: Money
  comparison: 'BELOW' | 'ABOVE' | 'EQUAL' | null
}

// On a phone each row is a card: the cells stack with their column's name before the value.
const cell =
  'px-3 py-2 text-right tabular-nums max-md:flex max-md:justify-between max-md:gap-3 max-md:px-0 max-md:py-1 max-md:before:font-normal max-md:before:text-text-muted max-md:before:content-[attr(data-label)]'
const rowHead =
  'px-3 py-2 text-left font-medium max-md:block max-md:px-0 max-md:pb-1 max-md:text-base'

/** The months, newest first, and a total row. It only formats what the API sent. */
export function MonthsTable({ months, totals }: Props) {
  const intl = useIntl()
  const formatMoney = useFormatMoney()
  const money = (value: Money) => (value ? formatMoney(parseCents(value.amount)) : null)
  const label = (id: keyof typeof venturesMessages) => intl.formatMessage({ id })
  const none = label('ventures.real.none')

  function monthLabel(month: string): string {
    const short = intl.formatDate(`${month}-01T00:00:00Z`, { month: 'short', timeZone: 'UTC' })
    return `${short.replace(/\.$/, '')}/${month.slice(0, 4)}`
  }

  const row = (key: string, head: string, values: RowValues, strong = false) => (
    <tr
      key={key}
      className={`border-t border-border max-md:block max-md:rounded-card max-md:border max-md:p-3 ${strong ? 'font-semibold' : ''}`}
    >
      <th scope="row" className={rowHead}>
        {head}
      </th>
      <td className={cell} data-label={label('ventures.real.income')}>
        {money(values.income)}
      </td>
      <td className={cell} data-label={label('ventures.real.expense')}>
        {money(values.expense)}
      </td>
      <td className={cell} data-label={label('ventures.real.result')}>
        {money(values.result)}
      </td>
      <td className={cell} data-label={label('ventures.real.planned')}>
        {money(values.planned) ?? none}
      </td>
      <td className={cell} data-label={label('ventures.real.difference')}>
        {values.comparison ? label(`ventures.real.comparison.${values.comparison}`) : none}
      </td>
    </tr>
  )

  return (
    <table className="w-full border-collapse text-sm max-md:block">
      <caption className="mb-2 text-left font-semibold">{label('ventures.real.table')}</caption>
      <thead className="max-md:sr-only">
        <tr>
          <th scope="col" className="px-3 py-2 text-left font-medium">
            {label('ventures.real.month')}
          </th>
          {COLUMNS.map((column) => (
            <th key={column} scope="col" className="px-3 py-2 text-right font-medium">
              {label(`ventures.real.${column}`)}
            </th>
          ))}
        </tr>
      </thead>
      <tbody className="max-md:flex max-md:flex-col max-md:gap-2">
        {months.map((month) =>
          row(month.month, monthLabel(month.month), {
            income: month.income,
            expense: month.expense,
            result: month.result,
            planned: month.plannedResult,
            comparison: month.comparison,
          }),
        )}
        {row(
          'total',
          label('ventures.real.total'),
          {
            income: totals.income,
            expense: totals.expense,
            result: totals.result,
            planned: totals.plannedResult,
            comparison: null,
          },
          true,
        )}
      </tbody>
    </table>
  )
}

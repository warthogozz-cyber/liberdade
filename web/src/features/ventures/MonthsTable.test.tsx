import { cleanup, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { ActualVsPlanned } from '@/shared/api/ventures'
import { registerMessages } from '@/shared/i18n'
import { renderWithProviders } from '@/shared/testing'
import { venturesMessages } from './messages.pt-BR'
import { MonthsTable } from './MonthsTable'

registerMessages(venturesMessages)
afterEach(cleanup)

const brl = (amount: string) => ({ amount, currency: 'BRL' })
const months: ActualVsPlanned['months'] = [
  {
    month: '2026-11',
    income: brl('300.00'),
    expense: brl('100.00'),
    result: brl('200.00'),
    plannedIncome: brl('400.00'),
    plannedCost: brl('130.00'),
    plannedResult: brl('270.00'),
    comparison: 'BELOW',
    difference: brl('70.00'),
  },
  {
    month: '2026-10',
    income: brl('500.00'),
    expense: brl('100.00'),
    result: brl('400.00'),
    plannedIncome: brl('400.00'),
    plannedCost: brl('130.00'),
    plannedResult: brl('270.00'),
    comparison: 'ABOVE',
    difference: brl('130.00'),
  },
]
const totals: ActualVsPlanned['totals'] = {
  income: brl('800.00'),
  expense: brl('200.00'),
  result: brl('600.00'),
  plannedIncome: brl('800.00'),
  plannedCost: brl('260.00'),
  plannedResult: brl('540.00'),
  comparison: 'ABOVE',
  difference: brl('60.00'),
}

describe('MonthsTable', () => {
  it('neutralComparison', () => {
    const { container } = renderWithProviders(<MonthsTable months={months} totals={totals} />)

    expect(screen.getByText(/^R\$\s70,00 abaixo do previsto$/)).toBeVisible()
    expect(screen.getByText(/^R\$\s130,00 acima do previsto$/)).toBeVisible()
    expect(screen.getByText(/^R\$\s60,00 acima do previsto$/)).toBeVisible()
    expect(container.innerHTML).not.toMatch(/danger|error|red-|warning/)
  })

  it('tableSemantics', () => {
    renderWithProviders(<MonthsTable months={months} totals={totals} />)

    const table = screen.getByRole('table', { name: 'Real e previsto, mês a mês' })
    expect(table.querySelector('caption')).not.toBeNull()
    const headers = within(table).getAllByRole('columnheader')
    expect(headers.length).toBeGreaterThan(0)
    for (const header of headers) expect(header).toHaveAttribute('scope', 'col')
    const rowHeaders = within(table).getAllByRole('rowheader')
    expect(rowHeaders).toHaveLength(3)
    for (const header of rowHeaders) expect(header).toHaveAttribute('scope', 'row')
    expect(within(table).getByRole('rowheader', { name: 'nov/2026' })).toBeVisible()
    expect(within(table).getByRole('rowheader', { name: 'Total' })).toBeVisible()
  })

  it('monthsWithoutPlanned', () => {
    const before = {
      month: '2026-08',
      income: brl('50.00'),
      expense: brl('20.00'),
      result: brl('30.00'),
      plannedIncome: null,
      plannedCost: null,
      plannedResult: null,
      comparison: null,
      difference: null,
    }
    renderWithProviders(
      <MonthsTable
        months={[before]}
        totals={{ ...totals, plannedResult: null, comparison: null, difference: null }}
      />,
    )

    const row = screen.getByRole('rowheader', { name: 'ago/2026' }).closest('tr')
    expect(row).not.toBeNull()
    expect(within(row as HTMLElement).getAllByText('—')).toHaveLength(2)
    expect(screen.queryByText(/do previsto/)).not.toBeInTheDocument()
    expect(
      within(row as HTMLElement).queryByText(/R\$\s*\d/, { selector: 'td:last-child' }),
    ).toBeNull()
  })

  it('equalShowsNoAmount', () => {
    const equal = { ...months[0], comparison: 'EQUAL' as const, difference: brl('0.00') }
    renderWithProviders(<MonthsTable months={[equal]} totals={totals} />)

    expect(screen.getByText('igual ao previsto')).toBeVisible()
  })
})

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { IntlProvider } from 'react-intl'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { appMessages, locale } from '@/shared/i18n'
import { ColumnMapping } from './ColumnMapping'
import { linesFromCsv, type CsvMapping } from './csvDetect'

afterEach(cleanup)

const rows = [
  ['Quando', 'Quem', 'Quanto', 'Resto'],
  ['03/11/2026', 'PADARIA', '-45,90', '100,00'],
  ['04/11/2026', 'MERCADO', '-12,50', '87,50'],
]

const initial: CsvMapping = {
  headerRow: 0,
  date: 0,
  description: 1,
  amount: { kind: 'signed', column: 2 },
  decimal: ',',
}

function renderScreen(start: CsvMapping, onConfirm = vi.fn()) {
  render(
    <IntlProvider locale={locale} messages={appMessages()}>
      <ColumnMapping rows={rows} initial={start} onConfirm={onConfirm} />
    </IntlProvider>,
  )
  return onConfirm
}

describe('ColumnMapping', () => {
  it('continueNeedsAllColumns', () => {
    const unset: CsvMapping = { ...initial, amount: { kind: 'signed', column: -1 } }
    const onConfirm = renderScreen(unset)
    const next = screen.getByRole('button', { name: 'Continuar' })
    expect(next).toHaveProperty('disabled', true)

    fireEvent.change(screen.getByLabelText('Valor'), { target: { value: '2' } })
    expect(next).toHaveProperty('disabled', false)
    fireEvent.click(next)
    expect(onConfirm).toHaveBeenCalledWith({
      headerRow: 0,
      date: 0,
      description: 1,
      amount: { kind: 'signed', column: 2 },
      decimal: ',',
    })
  })

  it('splitModeAsksForDebitAndCredit', () => {
    const onConfirm = renderScreen(initial)
    fireEvent.change(screen.getByLabelText('Como o valor aparece'), { target: { value: 'split' } })
    expect(screen.queryByLabelText('Valor')).toBeNull()
    const next = screen.getByRole('button', { name: 'Continuar' })
    expect(next).toHaveProperty('disabled', true)

    fireEvent.change(screen.getByLabelText('Débito'), { target: { value: '2' } })
    fireEvent.change(screen.getByLabelText('Crédito'), { target: { value: '3' } })
    fireEvent.click(next)
    expect(onConfirm).toHaveBeenCalledWith(
      expect.objectContaining({ amount: { kind: 'split', debit: 2, credit: 3 } }),
    )
  })

  it('recomputesTheDecimalFromTheChosenColumn', () => {
    const wrongGuess: CsvMapping = {
      ...initial,
      decimal: '.',
      amount: { kind: 'signed', column: 0 },
    }
    const onConfirm = renderScreen(wrongGuess)
    fireEvent.change(screen.getByLabelText('Valor'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))
    const confirmed = onConfirm.mock.calls[0]?.[0] as CsvMapping
    expect(confirmed.decimal).toBe(',')
    expect(linesFromCsv(rows, confirmed)[0]?.cents).toBe(-4590n)
  })

  it('showsTheFirstRowsAsASample', () => {
    renderScreen(initial)
    expect(screen.getByRole('table', { name: 'Primeiras linhas do arquivo' })).toBeTruthy()
    expect(screen.getByText('PADARIA')).toBeTruthy()
    expect(screen.getByText('MERCADO')).toBeTruthy()
  })
})

import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { ActualVsPlanned } from '@/shared/api/ventures'
import { registerMessages } from '@/shared/i18n'
import { renderWithProviders } from '@/shared/testing'
import { venturesMessages } from './messages.pt-BR'
import { RealPaybackCards } from './RealPaybackCards'

registerMessages(venturesMessages)
afterEach(cleanup)

const brl = (amount: string) => ({ amount, currency: 'BRL' })
const planned: ActualVsPlanned['planned'] = {
  initialInvestment: brl('800.00'),
  paybackMonths: 2,
  paybackState: 'MONTHS',
}

function show(real: ActualVsPlanned['real']) {
  renderWithProviders(<RealPaybackCards planned={planned} real={real} />)
}

describe('RealPaybackCards', () => {
  it('paidBack', () => {
    show({ state: 'PAID_BACK', months: 3, investment: brl('900.00'), missing: null })

    expect(screen.getByText('se pagou em 3 meses')).toBeVisible()
    expect(screen.getByText('2 meses')).toBeVisible()
    expect(screen.getByText(/R\$\s900,00/)).toBeVisible()
  })

  it('notYet', () => {
    show({ state: 'NOT_YET', months: null, investment: brl('900.00'), missing: brl('500.00') })

    expect(screen.getByText(/ainda não se pagou: faltam R\$\s500,00/)).toBeVisible()
    expect(screen.queryByText(/se pagou em/)).not.toBeInTheDocument()
  })

  it('neverNegativeShowsTheTip', () => {
    show({ state: 'NEVER_NEGATIVE', months: null, investment: null, missing: null })

    expect(screen.getByText('o resultado ficou positivo desde o início')).toBeVisible()
    expect(
      screen.getByText('Marque também as compras do começo para ver o investimento real.'),
    ).toBeVisible()
  })

  it('noData', () => {
    show({ state: 'NO_DATA', months: null, investment: null, missing: null })

    expect(screen.getByText('nenhum lançamento marcado ainda')).toBeVisible()
  })
})

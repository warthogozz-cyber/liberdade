import { cleanup, fireEvent, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { prdExampleVenture } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { HowWeCalculate } from './HowWeCalculate'

afterEach(cleanup)

function open() {
  fireEvent.click(screen.getByText('Como calculamos'))
}

describe('HowWeCalculate', () => {
  it('spellsOutThePrdExample', () => {
    renderWithProviders(<HowWeCalculate venture={prdExampleVenture()} />)

    expect(screen.getByRole('group')).not.toHaveAttribute('open')
    open()

    expect(screen.getByText('Margem: R$ 4,50 − R$ 1,80 = R$ 2,70')).toBeVisible()
    expect(
      screen.getByText(
        'Ponto de equilíbrio: R$ 270,00 ÷ R$ 2,70 = 100 unidades, arredondado para cima',
      ),
    ).toBeVisible()
    expect(screen.getByText('Payback: R$ 800,00 ÷ R$ 540,00 = 1,48 → 2 meses')).toBeVisible()
    expect(
      screen.getByText('ROI em 12 meses: (12 × R$ 540,00 − R$ 800,00) ÷ R$ 800,00 = 710,0%'),
    ).toBeVisible()
  })

  it('neverDividesByZeroOrNegative', () => {
    const base = prdExampleVenture()
    renderWithProviders(
      <HowWeCalculate
        venture={{
          ...base,
          figures: {
            ...base.figures,
            monthlyProfit: { amount: '-20.00', currency: 'BRL' },
            paybackMonths: null,
            paybackState: 'NEVER',
            roi12mPercent: '-130.0',
            breakEven: { state: 'NO_BREAK_EVEN', revenue: null, weightedMarginPercent: null },
          },
        }}
      />,
    )
    open()

    expect(screen.getByText('Payback: não se paga com esses números')).toBeVisible()
    expect(
      screen.getByText('Ponto de equilíbrio: não há ponto de equilíbrio com esses números'),
    ).toBeVisible()
    expect(screen.queryByText(/÷ −/)).not.toBeInTheDocument()
    expect(screen.getByText(/= −130,0%/)).toBeVisible()
  })

  it('severalProductsUseTheWeightedMargin', () => {
    const base = prdExampleVenture()
    const [product] = base.products
    const [figures] = base.figures.products
    if (!product || !figures) throw new Error('fixture')
    renderWithProviders(
      <HowWeCalculate
        venture={{
          ...base,
          products: [product, { ...product, id: 'p2', name: 'Trufa' }],
          figures: { ...base.figures, products: [figures, figures] },
        }}
      />,
    )
    open()

    expect(screen.getByText('Margem de Brigadeiro: R$ 4,50 − R$ 1,80 = R$ 2,70')).toBeVisible()
    expect(screen.getByText('Margem de Trufa: R$ 4,50 − R$ 1,80 = R$ 2,70')).toBeVisible()
    expect(screen.getByText('Ponto de equilíbrio: R$ 270,00 ÷ 60,0% = R$ 450,00')).toBeVisible()
  })
})

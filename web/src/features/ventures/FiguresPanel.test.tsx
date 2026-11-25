import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { prdExampleFigures } from '@/mocks/data'
import type { VentureFigures } from '@/shared/api/ventures'
import { renderWithProviders } from '@/shared/testing'
import { FiguresPanel } from './FiguresPanel'

afterEach(cleanup)

function render(figures: VentureFigures) {
  return renderWithProviders(<FiguresPanel figures={figures} currency="BRL" />)
}

describe('FiguresPanel', () => {
  it('prdExample', () => {
    render(prdExampleFigures())

    expect(screen.getByText(/R\$\s2,70/)).toBeVisible()
    expect(screen.getByText('60,0%')).toBeVisible()
    expect(screen.getByText('100 unidades')).toBeVisible()
    expect(screen.getByText(/R\$\s450,00/)).toBeVisible()
    expect(screen.getByText(/R\$\s540,00/)).toBeVisible()
    expect(screen.getByText('2 meses')).toBeVisible()
    expect(screen.getByText('710,0%')).toBeVisible()
    expect(
      screen.getByText('Com os números que você digitou. Não é promessa de resultado.'),
    ).toBeVisible()
    expect(screen.getByRole('region', { name: 'Prévia das contas' })).toHaveAttribute(
      'aria-live',
      'polite',
    )
  })

  it('neutralEdgeTexts', () => {
    const base = prdExampleFigures()
    const edges: VentureFigures = {
      ...base,
      paybackMonths: null,
      paybackState: 'NEVER',
      roi12mPercent: null,
      roiState: 'NO_INVESTMENT',
      breakEven: { state: 'NO_BREAK_EVEN', revenue: null, weightedMarginPercent: null },
      products: [
        {
          margin: { amount: '-0.50', currency: 'BRL' },
          marginPercent: '-12.5',
          priceCoversCost: false,
          breakEvenUnits: null,
        },
      ],
    }
    const { container, rerender } = render(edges)

    expect(screen.getByText('não se paga com esses números')).toBeVisible()
    expect(screen.getByText('não há ponto de equilíbrio com esses números')).toBeVisible()
    expect(screen.getByText('sem investimento inicial')).toBeVisible()
    expect(screen.getByText('O preço não cobre o custo deste produto')).toBeVisible()
    expect(screen.getByText('−12,5%')).toBeVisible()
    expect(container.innerHTML).not.toMatch(/negative|danger|error|red-/)

    rerender(
      <FiguresPanel
        currency="BRL"
        figures={{
          ...base,
          breakEven: { state: 'NEEDS_PLANNED_UNITS', revenue: null, weightedMarginPercent: null },
        }}
      />,
    )
    expect(screen.getByText('informe a venda prevista')).toBeVisible()
  })

  it('offlineSaysToConnect', () => {
    renderWithProviders(<FiguresPanel currency="BRL" offline />)

    expect(screen.getByText('Conecte-se para ver as contas.')).toBeVisible()
  })
})

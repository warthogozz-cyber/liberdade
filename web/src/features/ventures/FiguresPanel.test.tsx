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

  it('captions', () => {
    render(prdExampleFigures())

    expect(screen.getByText('Margem: o que sobra de cada venda.')).toBeVisible()
    expect(screen.getByText('Equilíbrio: quanto vender para não ter prejuízo.')).toBeVisible()
    expect(screen.getByText('Retorno: em quantos meses o investimento volta.')).toBeVisible()
    for (const label of [
      'Margem por unidade',
      'Margem',
      'Equilíbrio (faturamento)',
      'Retorno do investimento',
      'Retorno em 12 meses',
    ]) {
      expect(screen.getByText(label)).toBeVisible()
    }
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

  it('errorSaysItCouldNotCalculateWithoutAlarm', () => {
    const { container } = renderWithProviders(<FiguresPanel currency="BRL" error />)

    expect(screen.getByText('Não deu para calcular com esses números.')).toBeVisible()
    expect(screen.queryByText('Calculando…')).not.toBeInTheDocument()
    expect(container.innerHTML).not.toMatch(/negative|danger|error|red-|alert/)
  })

  it('errorWithOldFiguresSaysTheyAreOld', () => {
    const { container } = renderWithProviders(
      <FiguresPanel figures={prdExampleFigures()} currency="BRL" error />,
    )

    expect(
      screen.getByText('Não deu para atualizar. Estes números são da última conta que deu certo.'),
    ).toBeVisible()
    expect(screen.getByText('2 meses')).toBeVisible()
    expect(container.innerHTML).not.toMatch(/negative|danger|error|red-|alert/)
  })

  it('noOldFiguresNoticeWithoutAnError', () => {
    render(prdExampleFigures())

    expect(screen.queryByText(/Não deu para atualizar/)).not.toBeInTheDocument()
  })

  it('offlineSaysToConnect', () => {
    renderWithProviders(<FiguresPanel currency="BRL" offline />)

    expect(screen.getByText('Conecte-se para ver as contas.')).toBeVisible()
  })
})

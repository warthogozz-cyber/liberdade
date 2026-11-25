import { describe, expect, it } from 'vitest'
import { prdExampleFigures } from '@/mocks/data'
import { breakEvenText, paybackText, percentText, roiText } from './figuresText'

describe('figuresText', () => {
  it('negativeUsesUnicodeMinus', () => {
    expect(percentText('-88.0')).toBe('−88,0%')
    expect(percentText('60.0')).toBe('60,0%')
  })

  it('readsEachEdgeFromItsState', () => {
    const figures = prdExampleFigures()
    expect(paybackText(figures)).toBe('2 meses')
    expect(paybackText({ ...figures, paybackMonths: 1 })).toBe('1 mês')
    expect(paybackText({ ...figures, paybackMonths: null, paybackState: 'NEVER' })).toBe(
      'não se paga com esses números',
    )
    expect(roiText(figures)).toBe('710,0%')
    expect(roiText({ ...figures, roi12mPercent: null, roiState: 'NO_INVESTMENT' })).toBe(
      'sem investimento inicial',
    )
    expect(breakEvenText(figures)).toBeUndefined()
    expect(
      breakEvenText({ ...figures, breakEven: { ...figures.breakEven, state: 'NO_BREAK_EVEN' } }),
    ).toBe('não há ponto de equilíbrio com esses números')
    expect(
      breakEvenText({
        ...figures,
        breakEven: { ...figures.breakEven, state: 'NEEDS_PLANNED_UNITS' },
      }),
    ).toBe('informe a venda prevista')
  })
})

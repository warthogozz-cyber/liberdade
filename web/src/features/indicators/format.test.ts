import { describe, expect, it } from 'vitest'
import type { components } from '@/shared/api/schema'
import { formatDecimal, formatIndicatorValue, formatVariation, isMonthly } from './format'

type IndicatorView = components['schemas']['IndicatorView']

function view(unit: string, value: string | null): IndicatorView {
  return {
    code: 'X',
    name: 'X',
    unit,
    value,
    referenceDate: null,
    variation: null,
    source: { name: 'Banco Central do Brasil', url: null },
    updatedAt: null,
    stale: false,
  }
}

describe('format', () => {
  it('decimalUsesCommaAndMinusSign', () => {
    expect(formatDecimal('15.00')).toBe('15,00')
    expect(formatDecimal('-0.25')).toBe('−0,25')
  })

  it('valueByUnit', () => {
    expect(formatIndicatorValue(view('PERCENT_YEAR', '15.00'))).toBe('15,00% a.a.')
    expect(formatIndicatorValue(view('PERCENT_MONTH', '0.50'))).toBe('0,50% no mês')
    expect(formatIndicatorValue(view('PERCENT_12M', '4.00'))).toBe('4,00% em 12 meses')
    expect(formatIndicatorValue(view('BRL', '5.4100'))).toBe('R$ 5,4100')
    expect(formatIndicatorValue(view('BRL', null))).toBeNull()
  })

  it('variationByBasis', () => {
    expect(
      formatVariation({ basis: 'LAST_CHANGE', since: '2026-11-05', unit: 'PP', value: '0.50' }),
    ).toBe('+0,50 p.p. em 05/11')
    expect(
      formatVariation({ basis: 'PREVIOUS', since: '2026-10-01', unit: 'PP', value: '-0.10' }),
    ).toBe('−0,10 p.p. em relação a 01/10')
    expect(
      formatVariation({ basis: 'PREVIOUS', since: '2026-11-21', unit: 'PERCENT', value: '-0.41' }),
    ).toBe('−0,41% em relação a 21/11')
    expect(
      formatVariation({ basis: 'PREVIOUS', since: '2026-11-21', unit: 'PP', value: '0.00' }),
    ).toBe('0,00 p.p. em relação a 21/11')
  })

  it('monthlyVariationNamesTheMonth', () => {
    expect(
      formatVariation(
        { basis: 'PREVIOUS', since: '2026-10-01', unit: 'PP', value: '0.10' },
        'IPCA_MONTH',
      ),
    ).toBe('+0,10 p.p. em relação a outubro de 2026')
    expect(
      formatVariation(
        { basis: 'PREVIOUS', since: '2026-10-01', unit: 'PP', value: '0.10' },
        'SELIC_TARGET',
      ),
    ).toBe('+0,10 p.p. em relação a 01/10')
  })

  it('tellsMonthlySeriesFromDaily', () => {
    expect(isMonthly('IPCA_MONTH')).toBe(true)
    expect(isMonthly('IPCA_12M')).toBe(true)
    expect(isMonthly('SELIC_TARGET')).toBe(false)
    expect(isMonthly('USD_PTAX_SELL')).toBe(false)
  })
})

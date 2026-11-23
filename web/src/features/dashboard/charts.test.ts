import { describe, expect, it } from 'vitest'
import type { CategoryView, DashboardView } from '@/shared/api'
import {
  CHART_PALETTE,
  OTHERS_COLOR,
  balanceLineOption,
  categorySlices,
  comparisonOption,
  donutOption,
} from './charts'
import { formatMoney } from '@/shared/money'

const baseOpts = {
  animate: false,
  textColor: '#000000',
  format: (cents: bigint) => formatMoney(cents, 'BRL', 'pt-BR'),
}

const money = (amount: string) => ({ amount, currency: 'BRL' })

function category(id: string, name: string): CategoryView {
  return { id, kind: 'EXPENSE', name, icon: null, color: null, parentId: null, archivedAt: null }
}

function rows(count: number, withOthers: boolean): DashboardView['byCategory'] {
  const lines = Array.from({ length: count }, (_, index) => ({
    categoryId: `c${index}`,
    current: money(`${100 - index}.00`),
    previous: money('0.00'),
  }))
  return withOthers
    ? [...lines, { categoryId: null, current: money('5.00'), previous: money('1.00') }]
    : lines
}

const named = Array.from({ length: 6 }, (_, index) => category(`c${index}`, `Categoria ${index}`))

describe('charts', () => {
  it('colorsByPosition', () => {
    const slices = categorySlices(rows(6, true), named)

    expect(slices).toHaveLength(7)
    expect(slices.slice(0, 6).map((slice) => slice.color)).toEqual([...CHART_PALETTE])
    expect(slices[6]).toMatchObject({ id: null, label: 'Outras', color: OTHERS_COLOR })
    expect(slices[0]).toMatchObject({ id: 'c0', label: 'Categoria 0', current: 10000n })
    expect(slices[6].previous).toBe(100n)
  })

  it('donutUsesSliceValues', () => {
    const slices = categorySlices(
      [{ categoryId: 'm', current: money('1234.56'), previous: money('0.00') }],
      [category('m', 'Mercado')],
    )
    const option = donutOption(slices, { ...baseOpts, animate: true })
    const series = (option.series as { data: { name: string; value: number }[] }[])[0]

    expect(series.data[0]).toMatchObject({ name: 'Mercado', value: 1234.56 })
  })

  it('animationFollowsOption', () => {
    const slices = categorySlices(rows(2, false), named)
    const days = [{ day: '2026-11-01', balance: 100n }]
    for (const animate of [false, true]) {
      const opts = { ...baseOpts, animate }
      expect(donutOption(slices, opts).animation).toBe(animate)
      expect(comparisonOption(slices, { current: 'nov.', previous: 'out.' }, opts).animation).toBe(
        animate,
      )
      expect(balanceLineOption(days, opts).animation).toBe(animate)
    }
  })

  it('comparisonLabelsFitNarrowScreens', () => {
    const option = comparisonOption(
      categorySlices(rows(2, false), named),
      { current: 'nov.', previous: 'out.' },
      baseOpts,
    )
    // Long category names are cut and tilted instead of overlapping on a phone; the table has them all.
    expect(option.xAxis).toMatchObject({
      axisLabel: { interval: 0, rotate: 30, width: 72, overflow: 'truncate' },
    })
  })

  it('writes the value axis with the given format', () => {
    const option = balanceLineOption([], { ...baseOpts, format: (cents) => `<${cents}>` })
    const formatter = (option.yAxis as { axisLabel: { formatter: (value: unknown) => string } })
      .axisLabel.formatter
    expect(formatter(12.34)).toBe('<1234>')
  })

  it('lineHasEveryDay', () => {
    const days = Array.from({ length: 30 }, (_, index) => ({
      day: `2026-11-${String(index + 1).padStart(2, '0')}`,
      balance: BigInt(index * 100),
    }))
    const option = balanceLineOption(days, baseOpts)
    const axis = option.xAxis as { data: string[] }
    const series = (option.series as { data: number[] }[])[0]

    expect(axis.data).toHaveLength(30)
    expect(axis.data[0]).toBe('1')
    expect(series.data).toHaveLength(30)
    expect(series.data[29]).toBe(29)
  })
})

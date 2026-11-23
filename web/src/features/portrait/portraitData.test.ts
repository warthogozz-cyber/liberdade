import { describe, expect, it } from 'vitest'
import type { DashboardView } from '@/shared/api'
import { buildPortrait } from './portraitData'

const money = (cents: number) => ({
  amount: `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`,
  currency: 'BRL',
})

type Line = { id: string | null; cents: number }

function view(lines: Line[], income = 0, expenses?: number): DashboardView {
  const spent = expenses ?? lines.reduce((sum, line) => sum + line.cents, 0)
  return {
    month: '2026-11',
    income: money(income),
    expenses: money(spent),
    result: money(Math.abs(income - spent)),
    balance: money(0),
    byCategory: lines.map((line) => ({
      categoryId: line.id,
      current: money(line.cents),
      previous: money(0),
    })),
    dailyBalance: [],
  } as DashboardView
}

const names = (count: number) =>
  new Map(Array.from({ length: count }, (_, i) => [`c${i}`, `Cat ${i}`]))
const sum = (slices: { percent: number }[]) =>
  slices.reduce((total, slice) => total + slice.percent, 0)

describe('buildPortrait', () => {
  it('mergesSixthIntoOther', () => {
    const lines: Line[] = [
      { id: 'c0', cents: 60000 },
      { id: 'c1', cents: 50000 },
      { id: 'c2', cents: 40000 },
      { id: 'c3', cents: 30000 },
      { id: 'c4', cents: 20000 },
      { id: 'c5', cents: 10000 },
      { id: null, cents: 10000 },
    ]
    const { slices } = buildPortrait(view(lines), names(6))
    expect(slices).toHaveLength(6)
    expect(slices.slice(0, 5).map((s) => s.label)).toEqual([
      'Cat 0',
      'Cat 1',
      'Cat 2',
      'Cat 3',
      'Cat 4',
    ])
    expect(slices[5]).toEqual({ label: 'Outras', percent: 9, other: true })
    expect(sum(slices)).toBe(100)
  })

  it('roundingRemainderGoesToOther', () => {
    const lines: Line[] = [
      { id: 'c0', cents: 3333 },
      { id: 'c1', cents: 3333 },
      { id: 'c2', cents: 3333 },
      { id: null, cents: 1 },
    ]
    const { slices } = buildPortrait(view(lines), names(3))
    expect(slices.map((s) => s.percent)).toEqual([33, 33, 33, 1])
    expect(slices[3].other).toBe(true)
    expect(sum(slices)).toBe(100)
  })

  it('tinyCategoryDoesNotGoNegative', () => {
    const lines: Line[] = [
      { id: 'c0', cents: 1 },
      { id: null, cents: 999999 },
    ]
    const { slices } = buildPortrait(view(lines), names(1))
    expect(slices).toEqual([{ label: 'Outras', percent: 100, other: true }])
  })

  it('unknownCategoryJoinsOther', () => {
    const lines: Line[] = [
      { id: 'c0', cents: 5000 },
      { id: 'gone', cents: 3000 },
      { id: null, cents: 2000 },
    ]
    const { slices } = buildPortrait(view(lines), names(1))
    expect(slices).toEqual([
      { label: 'Cat 0', percent: 50, other: false },
      { label: 'Outras', percent: 50, other: true },
    ])
  })

  it('zeroIncomeHidesIncomeBlock', () => {
    expect(buildPortrait(view([{ id: 'c0', cents: 100 }], 0), names(1)).income).toBeNull()
  })

  it('spentMoreThanIncome', () => {
    const data = buildPortrait(view([{ id: 'c0', cents: 125000 }], 100000), names(1))
    expect(data.income).toEqual({ kind: 'over', percent: 25 })
  })

  it('leftoverIncome', () => {
    const data = buildPortrait(view([{ id: 'c0', cents: 70000 }], 100000), names(1))
    expect(data.income).toEqual({ kind: 'left', percent: 30 })
  })

  it('emptyMonth', () => {
    const data = buildPortrait(view([], 100000), names(1))
    expect(data.empty).toBe(true)
    expect(data.slices).toEqual([])
    expect(data.month).toBe('novembro de 2026')
  })

  it('slicesAlwaysSumTo100', () => {
    let seed = 20261102
    const random = () => {
      seed = (seed + 0x6d2b79f5) | 0
      let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296
    }
    for (let run = 0; run < 1000; run++) {
      const count = 1 + Math.floor(random() * 7)
      const lines: Line[] = Array.from({ length: count }, (_, i) => ({
        id: `c${i}`,
        cents: 1 + Math.floor(random() * 10 ** 7),
      }))
      if (random() < 0.5) lines.push({ id: null, cents: 1 + Math.floor(random() * 10 ** 7) })
      const { slices } = buildPortrait(view(lines), names(8))
      expect(sum(slices)).toBe(100)
      expect(slices.every((s) => s.percent >= 0)).toBe(true)
      expect(slices.length).toBeLessThanOrEqual(6)
    }
  })
})

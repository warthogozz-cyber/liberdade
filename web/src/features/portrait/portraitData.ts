import type { DashboardView } from '@/shared/api'
import { parseCents } from '@/shared/money'

export type PortraitSlice = { label: string; percent: number; other: boolean }
export type IncomeBlock = { kind: 'left' | 'over'; percent: number }
export type PortraitData = {
  month: string
  slices: PortraitSlice[]
  income: IncomeBlock | null
  empty: boolean
}

const OTHERS = 'Outras'
/** The largest categories kept by name; the next one joins "Outras". */
const NAMED = 5

const monthName = new Intl.DateTimeFormat('pt-BR', {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
})

/** The nearest whole percent of `part` in `whole` (halves go up), in exact integer arithmetic. */
function percentOf(part: bigint, whole: bigint): number {
  return Number((part * 200n + whole) / (2n * whole))
}

/**
 * The month card's numbers, in percent only: the 5 largest expense categories, the rest as "Outras"
 * (which also takes the rounding remainder, so the slices always add up to 100), and how much of the
 * income was left. Pure: no DOM, no network.
 */
export function buildPortrait(view: DashboardView, names: Map<string, string>): PortraitData {
  const month = monthName.format(new Date(`${view.month}-01T00:00:00Z`))
  const lines = view.byCategory.map((line) => ({
    id: line.categoryId,
    cents: parseCents(line.current.amount),
  }))
  const total = lines.reduce((sum, line) => sum + line.cents, 0n)
  const income = incomeBlock(view)
  if (total <= 0n) return { month, slices: [], income, empty: true }

  const named = lines
    // A category without a name (deleted, or not loaded) has no slice of its own: it is in "Outras".
    .filter((line) => line.id !== null && names.has(line.id) && line.cents > 0n)
    .sort((a, b) => (a.cents === b.cents ? 0 : a.cents > b.cents ? -1 : 1))
    .slice(0, NAMED)
  const slices: PortraitSlice[] = named
    .map((line) => ({
      label: names.get(line.id!)!,
      percent: percentOf(line.cents, total),
      other: false,
    }))
    .filter((slice) => slice.percent > 0)

  let rest = 100 - slices.reduce((sum, slice) => sum + slice.percent, 0)
  // Rounding up can overshoot 100 by a point or two: the biggest slice gives it back.
  while (rest < 0) {
    const biggest = slices.reduce((max, slice) => (slice.percent > max.percent ? slice : max))
    biggest.percent -= 1
    rest += 1
  }
  if (rest > 0) slices.push({ label: OTHERS, percent: rest, other: true })
  return { month, slices, income, empty: false }
}

function incomeBlock(view: DashboardView): IncomeBlock | null {
  const income = parseCents(view.income.amount)
  if (income <= 0n) return null
  const result = income - parseCents(view.expenses.amount)
  return result >= 0n
    ? { kind: 'left', percent: percentOf(result, income) }
    : { kind: 'over', percent: percentOf(-result, income) }
}

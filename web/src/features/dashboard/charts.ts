import type { CategoryView, DashboardView } from '@/shared/api'
import { formatBRL, parseCents } from '@/shared/money'

// Pure on purpose: no DOM and no runtime import of ECharts, so the options are plain data. Only
// `EChart.tsx` talks to the library. The colors are HEX because the renderer parses it reliably, and
// each one has at least 3:1 contrast against both surfaces (#ffffff and #12303a), because these
// options do not know the theme.

/** The categorical colors in order: teal first, then the rest of the design system's hues. */
export const CHART_PALETTE = [
  '#009689', // teal 600
  '#3080ff', // blue 500
  '#ad46ff', // purple 500
  '#f6339a', // pink 500
  '#f54900', // orange 600
  '#00a63e', // green 600
] as const

/** The slice that groups what did not fit in the palette, and categories without a color. */
export const OTHERS_COLOR = '#7c8b8f'

/** An ECharts option, as plain data. */
export type ChartOption = Record<string, unknown>

export interface ChartOpts {
  animate: boolean
  /** The theme's text color, for the axes. */
  textColor: string
}

export interface CategorySlice {
  /** `null` for "Outras": the rest, which is not one category. */
  id: string | null
  label: string
  current: bigint
  previous: bigint
  color: string
}

/** The dashboard's category lines as slices: names from `categories`, color by position (D8). */
export function categorySlices(
  byCategory: DashboardView['byCategory'],
  categories: readonly CategoryView[],
  othersLabel = 'Outras',
): CategorySlice[] {
  const names = new Map(categories.map((category) => [category.id, category.name]))
  return byCategory.map((line, index) => {
    const id = line.categoryId
    return {
      id,
      label: id === null ? othersLabel : (names.get(id) ?? othersLabel),
      current: parseCents(line.current.amount),
      previous: parseCents(line.previous.amount),
      color: id === null ? OTHERS_COLOR : (CHART_PALETTE[index] ?? OTHERS_COLOR),
    }
  })
}

/** Cents as reais, for the chart's scale only: labels are written with `formatBRL`. */
const reais = (cents: bigint): number => Number(cents) / 100
const centsOf = (value: number): bigint => BigInt(Math.round(value * 100))
const money = (value: unknown): string => formatBRL(centsOf(Number(value)))

function axisStyle(textColor: string) {
  return {
    axisLabel: { color: textColor },
    axisLine: { lineStyle: { color: textColor, opacity: 0.4 } },
    splitLine: { lineStyle: { color: textColor, opacity: 0.15 } },
  }
}

export function donutOption(slices: readonly CategorySlice[], opts: ChartOpts): ChartOption {
  return {
    animation: opts.animate,
    series: [
      {
        type: 'pie',
        radius: ['55%', '85%'],
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderRadius: 4 },
        data: slices.map((slice) => ({
          id: slice.id,
          name: slice.label,
          value: reais(slice.current),
          itemStyle: { color: slice.color },
        })),
      },
    ],
  }
}

/** Grouped bars, one pair per category: last month, then this month. */
export function comparisonOption(
  slices: readonly CategorySlice[],
  monthLabels: { current: string; previous: string },
  opts: ChartOpts,
): ChartOption {
  return {
    animation: opts.animate,
    grid: { left: 8, right: 8, top: 16, bottom: 8, containLabel: true },
    dataset: {
      source: [
        ['category', monthLabels.previous, monthLabels.current],
        ...slices.map((slice) => [slice.label, reais(slice.previous), reais(slice.current)]),
      ],
    },
    xAxis: {
      type: 'category',
      // Long names are cut and tilted so they do not overlap on a phone; the table has them all.
      axisLabel: {
        color: opts.textColor,
        interval: 0,
        rotate: 30,
        width: 72,
        overflow: 'truncate',
      },
    },
    yAxis: {
      type: 'value',
      ...axisStyle(opts.textColor),
      axisLabel: { color: opts.textColor, formatter: money },
    },
    series: [
      { type: 'bar', itemStyle: { color: OTHERS_COLOR } },
      { type: 'bar', itemStyle: { color: CHART_PALETTE[0] } },
    ],
  }
}

/** The family's balance at the end of each day, one point per day. */
export function balanceLineOption(
  days: ReadonlyArray<{ day: string; balance: bigint }>,
  opts: ChartOpts,
): ChartOption {
  return {
    animation: opts.animate,
    grid: { left: 8, right: 16, top: 16, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: days.map(({ day }) => String(Number(day.slice(8, 10)))),
      axisLabel: { color: opts.textColor },
      axisLine: { lineStyle: { color: opts.textColor, opacity: 0.4 } },
    },
    yAxis: {
      type: 'value',
      scale: true,
      ...axisStyle(opts.textColor),
      axisLabel: { color: opts.textColor, formatter: money },
    },
    series: [
      {
        type: 'line',
        data: days.map(({ balance }) => reais(balance)),
        showSymbol: true,
        symbolSize: 5,
        lineStyle: { color: CHART_PALETTE[0], width: 2 },
        itemStyle: { color: CHART_PALETTE[0] },
      },
    ],
  }
}

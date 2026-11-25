import { useIntl } from 'react-intl'
import { registerMessages } from '@/shared/i18n'
import { formatMoney, parseCents } from '@/shared/money'
import type { VentureView } from '@/shared/api/ventures'
import { breakEvenText, paybackText, percentText, roiText } from './figuresText'
import { venturesMessages } from './messages.pt-BR'

registerMessages(venturesMessages)

/**
 * The investment over the monthly profit with two decimals, "1,48", from the cents with no `Number`.
 * Only for a profit and an investment above zero: the caller shows a neutral text otherwise.
 */
function ratioText(investment: bigint, profit: bigint): string {
  const hundredths = (investment * 200n + profit) / (2n * profit)
  return `${hundredths / 100n},${String(hundredths % 100n).padStart(2, '0')}`
}

/**
 * The project's arithmetic spelled out, folded by default. Every result comes from the API's answer;
 * the only number made here is the payback's ratio. A result that has no formula (no break-even, a
 * payback that never comes) is the neutral text of the figures panel, never a division by zero.
 */
export function HowWeCalculate({ venture }: { venture: VentureView }) {
  const intl = useIntl()
  const { figures, currency, products } = venture
  const money = (amount: string) => formatMoney(parseCents(amount), currency, intl.locale)
  const t = (id: keyof typeof venturesMessages, values: Record<string, string | number>) =>
    intl.formatMessage({ id }, values)

  const lines: string[] = products.map((product, index) => {
    const margin = figures.products[index]?.margin.amount ?? '0.00'
    const values = {
      name: product.name,
      price: money(product.unitPrice.amount),
      cost: money(product.unitVariableCost.amount),
      margin: money(margin),
    }
    return t(products.length > 1 ? 'ventures.how.marginOf' : 'ventures.how.margin', values)
  })

  const fixed = money(figures.fixedMonthly.amount)
  const edge = breakEvenText(figures)
  const onlyProduct = figures.products[0]
  if (edge !== undefined) {
    lines.push(t('ventures.how.breakEvenEdge', { text: edge }))
  } else if (products.length === 1 && onlyProduct && onlyProduct.breakEvenUnits !== null) {
    lines.push(
      t('ventures.how.breakEvenUnits', {
        fixed,
        margin: money(onlyProduct.margin.amount),
        units: onlyProduct.breakEvenUnits,
      }),
    )
  } else if (figures.breakEven.revenue && figures.breakEven.weightedMarginPercent !== null) {
    lines.push(
      t('ventures.how.breakEvenRevenue', {
        fixed,
        percent: percentText(figures.breakEven.weightedMarginPercent),
        revenue: money(figures.breakEven.revenue.amount),
      }),
    )
  } else {
    lines.push(
      t('ventures.how.breakEvenEdge', {
        text: intl.formatMessage({ id: 'ventures.figures.noBreakEven' }),
      }),
    )
  }

  const profit = money(figures.monthlyProfit.amount)
  const investment = money(figures.initialInvestment.amount)
  lines.push(t('ventures.how.profit', { margin: money(figures.totalMargin.amount), fixed, profit }))

  const investmentCents = parseCents(figures.initialInvestment.amount)
  const profitCents = parseCents(figures.monthlyProfit.amount)
  if (figures.paybackState === 'MONTHS' && investmentCents > 0n && profitCents > 0n) {
    lines.push(
      t('ventures.how.payback', {
        investment,
        profit,
        ratio: ratioText(investmentCents, profitCents),
        months: paybackText(figures),
      }),
    )
  } else {
    lines.push(t('ventures.how.paybackEdge', { text: paybackText(figures) }))
  }

  if (figures.roiState === 'OK' && figures.roi12mPercent !== null) {
    lines.push(t('ventures.how.roi', { profit, investment, roi: roiText(figures) }))
  } else {
    lines.push(t('ventures.how.roiEdge', { text: roiText(figures) }))
  }

  return (
    <details className="rounded-card bg-surface p-4 text-sm">
      <summary className="min-h-12 cursor-pointer py-3 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
        {intl.formatMessage({ id: 'ventures.how.title' })}
      </summary>
      <ul className="mt-2 flex flex-col gap-2 tabular-nums">
        {lines.map((line, index) => (
          <li key={index}>{line}</li>
        ))}
      </ul>
    </details>
  )
}

import { useIntl } from 'react-intl'
import { registerMessages } from '@/shared/i18n'
import { formatMoney, parseCents } from '@/shared/money'
import type { VentureFigures } from '@/shared/api/ventures'
import { breakEvenText, paybackText, percentText, roiText } from './figuresText'
import { venturesMessages } from './messages.pt-BR'

registerMessages(venturesMessages)

interface FiguresPanelProps {
  /** Without figures the panel says it is waiting (or, offline, that it needs the connection). */
  figures?: VentureFigures | undefined
  currency: string
  offline?: boolean
  /** A newer draft is on its way: the figures shown are the last ones. */
  pending?: boolean
  /** The last calculation failed: say so, calmly, and that figures still shown are the old ones. */
  error?: boolean
  /** The products' names, in the order of `figures.products`; "Produto n" when they do not match. */
  productNames?: readonly string[]
  /** Announces changes to a screen reader. Off on a page whose figures only change on a reload. */
  live?: boolean
}

/**
 * The project's figures, as the API computed them. Read-only and neutral: a project that does not
 * pay back, or a price below cost, is said in plain words, never in alarm colors (spec 6.3).
 */
export function FiguresPanel({
  figures,
  currency,
  offline = false,
  pending = false,
  error = false,
  productNames = [],
  live = true,
}: FiguresPanelProps) {
  const intl = useIntl()
  const money = (amount: string) => formatMoney(parseCents(amount), currency, intl.locale)
  const names = figures && productNames.length === figures.products.length ? productNames : []
  const breakEvenEdge = figures ? breakEvenText(figures) : undefined

  return (
    <section
      aria-live={live ? 'polite' : undefined}
      aria-busy={pending}
      aria-label={intl.formatMessage({ id: 'ventures.figures.title' })}
      className="flex flex-col gap-3 rounded-card bg-surface p-4 text-sm"
    >
      <h2 className="font-semibold">{intl.formatMessage({ id: 'ventures.figures.title' })}</h2>
      {offline ? (
        <p className="text-text-muted">{intl.formatMessage({ id: 'ventures.figures.offline' })}</p>
      ) : figures === undefined ? (
        <p className="text-text-muted">
          {intl.formatMessage({
            id: error ? 'ventures.figures.error' : 'ventures.figures.loading',
          })}
        </p>
      ) : (
        <>
          {error && (
            <p className="text-text-muted">
              {intl.formatMessage({ id: 'ventures.figures.stale' })}
            </p>
          )}
          <ul className="flex flex-col gap-2">
            {figures.products.map((product, index) => (
              <li key={index} className="flex flex-col gap-1">
                <span className="font-medium">
                  {names[index] ||
                    intl.formatMessage({ id: 'ventures.figures.product' }, { n: index + 1 })}
                </span>
                <dl className="flex flex-col gap-1">
                  <Row label={intl.formatMessage({ id: 'ventures.figures.margin' })}>
                    {money(product.margin.amount)}
                  </Row>
                  <Row label={intl.formatMessage({ id: 'ventures.figures.marginPercent' })}>
                    {percentText(product.marginPercent)}
                  </Row>
                </dl>
                {product.priceCoversCost ? (
                  product.breakEvenUnits !== null && (
                    <p className="text-text-muted">
                      {intl.formatMessage(
                        { id: 'ventures.figures.units' },
                        { count: product.breakEvenUnits },
                      )}
                    </p>
                  )
                ) : (
                  <p className="text-text-muted">
                    {intl.formatMessage({ id: 'ventures.figures.priceBelowCost' })}
                  </p>
                )}
              </li>
            ))}
          </ul>
          <p className="text-sm text-text-muted">
            {intl.formatMessage({ id: 'ventures.caption.margin' })}
          </p>
          <dl className="flex flex-col gap-1 border-t border-text-muted/30 pt-3">
            <Row label={intl.formatMessage({ id: 'ventures.figures.breakEven' })}>
              {breakEvenEdge ??
                (figures.breakEven.revenue ? money(figures.breakEven.revenue.amount) : '')}
            </Row>
            <Row label={intl.formatMessage({ id: 'ventures.figures.monthlyProfit' })}>
              {money(figures.monthlyProfit.amount)}
            </Row>
            <Row label={intl.formatMessage({ id: 'ventures.figures.payback' })}>
              {paybackText(figures)}
            </Row>
            <Row label={intl.formatMessage({ id: 'ventures.figures.roi' })}>{roiText(figures)}</Row>
          </dl>
          <p className="text-sm text-text-muted">
            {intl.formatMessage({ id: 'ventures.caption.breakEven' })}
          </p>
          <p className="text-sm text-text-muted">
            {intl.formatMessage({ id: 'ventures.caption.payback' })}
          </p>
        </>
      )}
      <p className="text-text-muted">{intl.formatMessage({ id: 'ventures.figures.note' })}</p>
    </section>
  )
}

function Row({ label, children }: { label: string; children: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-text-muted">{label}</dt>
      <dd className="text-right tabular-nums">{children}</dd>
    </div>
  )
}

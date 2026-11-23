import { useState } from 'react'
import { useIntl } from 'react-intl'
import { legalConfig } from '@/features/legal/legalConfig'
import { ApiError } from '@/shared/api'
import { useSetTheme, type FounderTheme, type PlanDetails } from '@/shared/api/billing'
import { RefundSheet } from './RefundSheet'
import { ThemePicker } from './ThemePicker'
import './register'

// The same zone the API counts the refund window in (RefundWindow.java).
const DATE_ZONE = 'America/Sao_Paulo'

interface PlanStatusCardProps {
  ws: string
  plan: PlanDetails
  isOwner: boolean
  online: boolean
}

/** A running Founder plan: the seal, the end date, the theme and the refund while it is open. */
export function PlanStatusCard({ ws, plan, isOwner, online }: PlanStatusCardProps) {
  const intl = useIntl()
  const setTheme = useSetTheme(ws)
  const [refunding, setRefunding] = useState(false)
  const [error, setError] = useState<string>()
  // The theme just picked shows at once and stays until the plan is read again (or the save fails).
  const [picked, setPicked] = useState<FounderTheme | null>(null)

  const full = (value: string) =>
    intl.formatDate(value, {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      timeZone: DATE_ZONE,
    })
  const short = (value: string) =>
    intl.formatDate(value, { day: '2-digit', month: '2-digit', timeZone: DATE_ZONE })
  // The deadline is exclusive (00:00 of the day after the last one), so the hint names the day before it.
  const lastDay = (deadline: string) => short(new Date(Date.parse(deadline) - 1).toISOString())

  async function pick(theme: FounderTheme) {
    if (theme === (picked ?? plan.theme)) return
    setError(undefined)
    setPicked(theme)
    try {
      // Resolves after the plan is read again, so the picked theme hands over without a flicker.
      await setTheme.mutateAsync(theme)
    } catch (failure) {
      setError(
        (failure instanceof ApiError && failure.detail) ||
          intl.formatMessage({ id: 'billing.actionFailed' }),
      )
    } finally {
      setPicked(null)
    }
  }

  const paidOrderId = plan.paidOrderId
  const refundable = plan.canRefund && paidOrderId !== null
  return (
    <section className="flex flex-col gap-3">
      <h2 className="self-start rounded-full bg-(--founder-accent) px-3 py-1 text-lg font-semibold text-white">
        {intl.formatMessage({ id: 'billing.founder' })}
      </h2>
      {plan.endsAt !== null && (
        <p>
          {intl.formatMessage(
            { id: plan.canRefund ? 'billing.endsOn' : 'billing.endsOnLong' },
            { date: full(plan.endsAt) },
          )}
        </p>
      )}
      <p>{intl.formatMessage({ id: 'billing.noAutoCharge' })}</p>
      {isOwner && (
        <ThemePicker
          value={picked ?? plan.theme}
          disabled={!online}
          onPick={(theme) => void pick(theme)}
        />
      )}
      {error && (
        <p role="alert" className="text-negative">
          {error}
        </p>
      )}
      {refundable && (
        <>
          {plan.refundDeadline !== null && (
            <p>
              {intl.formatMessage(
                { id: 'billing.refund.hint' },
                { date: lastDay(plan.refundDeadline) },
              )}
            </p>
          )}
          <button
            type="button"
            disabled={!online}
            onClick={() => setRefunding(true)}
            className="flex min-h-12 items-center justify-center rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
          >
            {intl.formatMessage({ id: 'billing.refund.open' })}
          </button>
        </>
      )}
      {!plan.canRefund && (
        <p className="text-text-muted">
          {intl.formatMessage({ id: 'billing.contact' }, { email: legalConfig.contactEmail })}
        </p>
      )}
      {refunding && paidOrderId !== null && (
        <RefundSheet ws={ws} orderId={paidOrderId} onClose={() => setRefunding(false)} />
      )}
    </section>
  )
}

import { useState } from 'react'
import { useIntl } from 'react-intl'
import { ApiError } from '@/shared/api'
import { useStartCheckout } from '@/shared/api/billing'
import './register'

interface FounderOfferCardProps {
  ws: string
  /** The Owner of a family that can buy; anyone else only reads the offer. */
  canPurchase: boolean
  /** An order waits for payment: a second checkout could charge twice, so there is no button. */
  pending: boolean
  online: boolean
  assign?: (url: string) => void
}

/** The pre-sale offer (FOUNDER_2026, R$ 89,00, one year) and the button that starts the checkout. */
export function FounderOfferCard({
  ws,
  canPurchase,
  pending,
  online,
  assign,
}: FounderOfferCardProps) {
  const intl = useIntl()
  const checkout = useStartCheckout(ws, assign)
  const [error, setError] = useState<string>()

  async function start() {
    if (!online || checkout.isPending) return
    setError(undefined)
    try {
      await checkout.mutateAsync()
    } catch (failure) {
      setError(
        (failure instanceof ApiError && failure.detail) ||
          intl.formatMessage({ id: 'billing.actionFailed' }),
      )
    }
  }

  return (
    <section className="flex flex-col gap-3">
      <p>{intl.formatMessage({ id: 'billing.offer' })}</p>
      <p className="text-text-muted">{intl.formatMessage({ id: 'billing.offer.includes' })}</p>
      <p>{intl.formatMessage({ id: 'billing.noAutoCharge' })}</p>
      {canPurchase && !pending && (
        <>
          <button
            type="button"
            disabled={!online || checkout.isPending}
            onClick={() => void start()}
            className="flex min-h-12 items-center justify-center rounded-field bg-primary px-4 font-medium text-on-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
          >
            {intl.formatMessage({ id: 'billing.offer.buy' })}
          </button>
          <p className="text-sm text-text-muted">
            {intl.formatMessage({ id: 'billing.offer.how' })}
          </p>
        </>
      )}
      {error && (
        <p role="alert" className="text-negative">
          {error}
        </p>
      )}
    </section>
  )
}

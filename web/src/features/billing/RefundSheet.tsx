import { useState } from 'react'
import { useIntl } from 'react-intl'
import { ApiError } from '@/shared/api'
import { useRequestRefund } from '@/shared/api/billing'
import { useOnline } from '@/shared/offline'
import { Sheet } from '@/shared/ui/Sheet'
import './register'

const button =
  'flex min-h-12 items-center justify-center rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50'

interface RefundSheetProps {
  ws: string
  /** The paid order (`paidOrderId`). */
  orderId: string
  onClose: () => void
}

/** A plain confirmation: no reason is asked. */
export function RefundSheet({ ws, orderId, onClose }: RefundSheetProps) {
  const intl = useIntl()
  const online = useOnline()
  const refund = useRequestRefund(ws)
  const [error, setError] = useState<string>()

  async function confirm() {
    if (!online || refund.isPending) return
    setError(undefined)
    try {
      await refund.mutateAsync(orderId)
      onClose()
    } catch (failure) {
      setError(
        (failure instanceof ApiError && failure.detail) ||
          intl.formatMessage({ id: 'billing.actionFailed' }),
      )
    }
  }

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      title={intl.formatMessage({ id: 'billing.refund.title' })}
    >
      <div className="flex flex-col gap-4">
        <p>{intl.formatMessage({ id: 'billing.refund.body' })}</p>
        {error && (
          <p role="alert" className="text-negative">
            {error}
          </p>
        )}
        {!online && (
          <p className="text-text-muted">{intl.formatMessage({ id: 'billing.offline' })}</p>
        )}
        <button
          type="button"
          disabled={!online || refund.isPending}
          onClick={() => void confirm()}
          className={`${button} bg-primary text-on-primary`}
        >
          {intl.formatMessage({ id: 'billing.refund.confirm' })}
        </button>
        <button
          type="button"
          onClick={onClose}
          className={`${button} border border-primary text-primary`}
        >
          {intl.formatMessage({ id: 'billing.refund.keep' })}
        </button>
      </div>
    </Sheet>
  )
}

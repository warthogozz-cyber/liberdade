import type { CSSProperties } from 'react'
import { useIntl } from 'react-intl'
import { useSearchParams } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'
import { usePlan, useRefreshOrder } from '@/shared/api/billing'
import { useOnline } from '@/shared/offline'
import { useDocumentTitle } from '@/shared/ui'
import { FounderOfferCard } from './FounderOfferCard'
import { founderThemes } from './founderThemes'
import { PlanStatusCard } from './PlanStatusCard'
import './register'

/** The family's plan: the Founder pre-sale, the order being confirmed, the theme and the refund. */
export function PlanPage({ assign }: { assign?: (url: string) => void }) {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'billing.title' })
  useDocumentTitle(title)
  const workspace = useCurrentWorkspace()
  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {workspace ? (
        <PlanBody ws={workspace.id} isOwner={workspace.role === 'OWNER'} assign={assign} />
      ) : (
        <p>{intl.formatMessage({ id: 'common.loading' })}</p>
      )}
    </main>
  )
}

function PlanBody({
  ws,
  isOwner,
  assign,
}: {
  ws: string
  isOwner: boolean
  assign?: (url: string) => void
}) {
  const intl = useIntl()
  const online = useOnline()
  const plan = usePlan(ws)
  const [search] = useSearchParams()
  const returned = search.get('pedido')
  const open = plan.data?.openOrder ?? null
  // Only the order the person came back to, and only while it still waits for payment.
  const waitingId =
    returned !== null && open?.id === returned && open.status === 'CREATED' ? returned : null
  useRefreshOrder(ws, waitingId)

  if (plan.isPending) return <p>{intl.formatMessage({ id: 'common.loading' })}</p>
  if (plan.isError) return <p role="alert">{intl.formatMessage({ id: 'billing.loadFailed' })}</p>

  const data = plan.data
  const founder = data.key === 'FAMILY'
  return (
    // The accent lives on this container only, not on the whole app.
    <div
      className="flex flex-col gap-4"
      style={
        founder
          ? ({ '--founder-accent': founderThemes[data.theme].accent } as CSSProperties)
          : undefined
      }
    >
      {!online && (
        <p className="text-text-muted">{intl.formatMessage({ id: 'billing.offline' })}</p>
      )}
      {founder ? (
        <PlanStatusCard ws={ws} plan={data} isOwner={isOwner} online={online} />
      ) : (
        <>
          <h2 className="text-lg font-semibold">{intl.formatMessage({ id: 'billing.free' })}</h2>
          {data.presaleOpen ? (
            <FounderOfferCard
              ws={ws}
              canPurchase={data.canPurchase}
              online={online}
              assign={assign}
            />
          ) : (
            <p>{intl.formatMessage({ id: 'billing.freeForever' })}</p>
          )}
        </>
      )}
      {waitingId !== null && <p>{intl.formatMessage({ id: 'billing.waiting' })}</p>}
      {open?.status === 'REFUND_PENDING' && (
        <section className="flex flex-col gap-2">
          {open.refundReason === 'DUPLICATE' ? (
            <p>{intl.formatMessage({ id: 'billing.duplicate' })}</p>
          ) : (
            <>
              <p className="font-medium">{intl.formatMessage({ id: 'billing.refundPending' })}</p>
              <p className="text-text-muted">
                {intl.formatMessage({ id: 'billing.refundPending.card' })}
              </p>
            </>
          )}
        </section>
      )}
      {!isOwner && (
        <p className="text-text-muted">{intl.formatMessage({ id: 'billing.notOwner' })}</p>
      )}
    </div>
  )
}

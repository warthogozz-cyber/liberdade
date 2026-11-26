import { lazy, Suspense } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import { useRoutes } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'
import { familyFeaturesOpen, usePlan } from '@/shared/api/billing'
import { registerMessages } from '@/shared/i18n'
import { PlanRequired } from '@/shared/ui/PlanRequired'
import { marketsMessages } from './messages.pt-BR'

registerMessages(marketsMessages)

// One lazy module for every Feira screen, so the app's own routes carry a single stub.
const FeiraPage = lazy(() => import('./FeiraPage').then((m) => ({ default: m.FeiraPage })))
const ReceiptPage = lazy(() =>
  import('./receipt/ReceiptPage').then((m) => ({ default: m.ReceiptPage })),
)
const MarketCardPage = lazy(() =>
  import('./MarketCardPage').then((m) => ({ default: m.MarketCardPage })),
)

function Loading() {
  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <p role="status">
        <FormattedMessage id="common.loading" />
      </p>
    </main>
  )
}

/**
 * The Feira screens under `/mais/feira`, behind the module guard of the app routes. On the free plan
 * (with the plan on sale) they give way to the plan screen. A plan that cannot be read, or that waits
 * for a connection, opens them: the server still refuses what the plan does not allow.
 */
export function MarketsRoutes() {
  const intl = useIntl()
  const workspace = useCurrentWorkspace()
  const plan = usePlan(workspace?.id)
  // `useRoutes`, not `<Routes>`: it keeps the router's route-element helpers out of the first load.
  const screen = useRoutes([
    { index: true, element: <FeiraPage /> },
    { path: 'mercado/:ref', element: <MarketCardPage /> },
    { path: 'nota', element: <ReceiptPage /> },
  ])
  // Offline with no saved plan the query waits for a connection: the saved Feira opens meanwhile.
  if (plan.isPending && plan.fetchStatus !== 'paused') return <Loading />
  if (plan.data && !familyFeaturesOpen(plan.data)) {
    return <PlanRequired title={intl.formatMessage({ id: 'markets.title' })} />
  }
  return <Suspense fallback={<Loading />}>{screen}</Suspense>
}

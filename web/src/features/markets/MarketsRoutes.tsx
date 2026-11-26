import { lazy, Suspense } from 'react'
import { FormattedMessage } from 'react-intl'
import { useRoutes } from 'react-router'

// One lazy module for every Feira screen, so the app's own routes carry a single stub.
const FeiraPage = lazy(() => import('./FeiraPage').then((m) => ({ default: m.FeiraPage })))
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

/** The Feira screens under `/mais/feira`, behind the module guard of the app routes. */
export function MarketsRoutes() {
  // `useRoutes`, not `<Routes>`: it keeps the router's route-element helpers out of the first load.
  const screen = useRoutes([
    { index: true, element: <FeiraPage /> },
    { path: 'mercado/:ref', element: <MarketCardPage /> },
  ])
  return <Suspense fallback={<Loading />}>{screen}</Suspense>
}

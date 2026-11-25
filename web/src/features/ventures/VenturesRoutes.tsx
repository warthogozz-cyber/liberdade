import { lazy, Suspense } from 'react'
import { FormattedMessage } from 'react-intl'
import { useRoutes } from 'react-router'

// One lazy module for every projects screen, so the app's own routes carry a single stub. Each
// screen is its own chunk: the list does not pull in the form.
const VenturesListPage = lazy(() =>
  import('./VenturesListPage').then((m) => ({ default: m.VenturesListPage })),
)
const StartPage = lazy(() => import('./StartPage').then((m) => ({ default: m.StartPage })))
const VentureEditPage = lazy(() =>
  import('./VentureEditPage').then((m) => ({ default: m.VentureEditPage })),
)

const VenturePage = lazy(() => import('./VenturePage').then((m) => ({ default: m.VenturePage })))

function Loading() {
  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <p role="status">
        <FormattedMessage id="common.loading" />
      </p>
    </main>
  )
}

/** The projects screens under `/mais/projetos`, behind the module and Youth guards of the app routes. */
export function VenturesRoutes() {
  // `useRoutes`, not `<Routes>`: it keeps the router's route-element helpers out of the first load.
  const screen = useRoutes([
    { index: true, element: <VenturesListPage /> },
    { path: 'comecar', element: <StartPage /> },
    { path: ':id/editar', element: <VentureEditPage /> },
    { path: ':id', element: <VenturePage /> },
    // Task 11 adds `investimentos` and `investimentos/simulador`.
  ])
  return <Suspense fallback={<Loading />}>{screen}</Suspense>
}

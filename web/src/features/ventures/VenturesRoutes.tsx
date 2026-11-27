import { lazy, Suspense } from 'react'
import { FormattedMessage } from 'react-intl'
import { useRoutes, type RouteObject } from 'react-router'

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

const ActualVsPlannedPage = lazy(() =>
  import('./ActualVsPlannedPage').then((m) => ({ default: m.ActualVsPlannedPage })),
)

const InvestmentsPage = lazy(() =>
  import('./investments/InvestmentsPage').then((m) => ({ default: m.InvestmentsPage })),
)
const InvestmentSimulatorPage = lazy(() =>
  import('./investments/InvestmentSimulatorPage').then((m) => ({
    default: m.InvestmentSimulatorPage,
  })),
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

// The one list of the screens: `venturesRoutePaths` reads it, so the help coverage test and the
// router never diverge.
const venturesRoutes: RouteObject[] = [
  { index: true, element: <VenturesListPage /> },
  { path: 'comecar', element: <StartPage /> },
  { path: ':id/editar', element: <VentureEditPage /> },
  { path: ':id/real', element: <ActualVsPlannedPage /> },
  { path: ':id', element: <VenturePage /> },
  { path: 'investimentos', element: <InvestmentsPage /> },
  { path: 'investimentos/simulador', element: <InvestmentSimulatorPage /> },
]

/** The inner paths of the projects screens (the list is the index, with no path of its own). */
// Not a component, but it must sit beside the routes it reads (see the note above).
// eslint-disable-next-line react-refresh/only-export-components
export const venturesRoutePaths: readonly string[] = venturesRoutes.flatMap((route) =>
  route.path === undefined ? [] : [route.path],
)

/** The projects screens under `/mais/projetos`, behind the module and Youth guards of the app routes. */
export function VenturesRoutes() {
  // `useRoutes`, not `<Routes>`: it keeps the router's route-element helpers out of the first load.
  const screen = useRoutes(venturesRoutes)
  return <Suspense fallback={<Loading />}>{screen}</Suspense>
}

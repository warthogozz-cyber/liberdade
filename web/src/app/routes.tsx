import type { RouteObject } from 'react-router'
import { AppShell } from '@/app/AppShell'
import { NotFoundPage } from '@/app/NotFoundPage'
import { RootRedirect } from '@/app/RootRedirect'
import { SessionLayout } from '@/app/SessionLayout'
import { WorkspaceLayout } from '@/app/WorkspaceLayout'
import { SignInPage } from '@/features/auth/SignInPage'
import { HomePage } from '@/features/dashboard/HomePage'
import { WelcomePage } from '@/features/onboarding/WelcomePage'
import { NewExpensePage } from '@/features/transactions/NewExpensePage'

// Secondary screens load on demand, so the first load stays small (chunk budget in CI). Copy this
// shape for a new one: the page exports its component by name. The first-expense path (`/inicio`,
// `/lancar`) and the layouts stay static.
export const routes: RouteObject[] = [
  { path: '/entrar', element: <SignInPage /> },
  {
    element: <SessionLayout />,
    children: [
      { index: true, element: <RootRedirect /> },
      { path: '/boas-vindas', element: <WelcomePage /> },
      {
        element: <WorkspaceLayout />,
        children: [
          // Full screen, without the navigation.
          { path: '/lancar', element: <NewExpensePage /> },
          {
            element: <AppShell />,
            children: [
              { path: '/inicio', element: <HomePage /> },
              {
                path: '/lancamentos',
                lazy: () =>
                  import('@/features/transactions/TransactionsPage').then((m) => ({
                    Component: m.TransactionsPage,
                  })),
              },
              {
                path: '/mais',
                lazy: () => import('@/app/MorePage').then((m) => ({ Component: m.MorePage })),
              },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]

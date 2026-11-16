import type { RouteObject } from 'react-router'
import { AppShell } from '@/app/AppShell'
import { MorePage } from '@/app/MorePage'
import { NotFoundPage } from '@/app/NotFoundPage'
import { RootRedirect } from '@/app/RootRedirect'
import { SessionLayout } from '@/app/SessionLayout'
import { WorkspaceLayout } from '@/app/WorkspaceLayout'
import { SignInPage } from '@/features/auth/SignInPage'
import { HomePage } from '@/features/dashboard/HomePage'
import { WelcomePage } from '@/features/onboarding/WelcomePage'
import { NewExpensePage } from '@/features/transactions/NewExpensePage'
import { TransactionsPage } from '@/features/transactions/TransactionsPage'

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
              { path: '/lancamentos', element: <TransactionsPage /> },
              { path: '/mais', element: <MorePage /> },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]

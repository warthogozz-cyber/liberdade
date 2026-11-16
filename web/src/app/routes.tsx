import type { RouteObject } from 'react-router'
import { AppShell } from '@/app/AppShell'
import { NotFoundPage } from '@/app/NotFoundPage'
import { RootRedirect } from '@/app/RootRedirect'
import { SessionLayout } from '@/app/SessionLayout'
import { WorkspaceLayout } from '@/app/WorkspaceLayout'
import { WelcomePage } from '@/features/onboarding/WelcomePage'

// Secondary screens load on demand, so the first load stays small (chunk budget in CI). Copy this
// shape for a new one: the page exports its component by name. The layouts and the welcome page (a
// new person's first screen) stay static. The new-expense route loads on demand too: its form pulls in the sheets and
// the dialog library (about 35 KB gzip), and the service worker precaches the chunk, so recording
// still works offline.
export const routes: RouteObject[] = [
  {
    path: '/entrar',
    lazy: () => import('@/features/auth/SignInPage').then((m) => ({ Component: m.SignInPage })),
  },
  {
    // Outside the session: the person opening a link usually has none yet.
    path: '/convite',
    lazy: () =>
      import('@/features/members/AcceptInvitationPage').then((m) => ({
        Component: m.AcceptInvitationPage,
      })),
  },
  {
    element: <SessionLayout />,
    children: [
      { index: true, element: <RootRedirect /> },
      { path: '/boas-vindas', element: <WelcomePage /> },
      {
        element: <WorkspaceLayout />,
        children: [
          // Full screen, without the navigation.
          {
            path: '/lancar',
            lazy: () =>
              import('@/features/transactions/NewTransactionPage').then((m) => ({
                Component: m.NewTransactionPage,
              })),
          },
          {
            element: <AppShell />,
            children: [
              {
                path: '/inicio',
                lazy: () =>
                  import('@/features/dashboard/HomePage').then((m) => ({
                    Component: m.HomePage,
                  })),
              },
              {
                path: '/lancamentos',
                lazy: () =>
                  import('@/features/transactions/TransactionsPage').then((m) => ({
                    Component: m.TransactionsPage,
                  })),
              },
              {
                path: '/painel',
                lazy: () =>
                  import('@/features/dashboard/DashboardPage').then((m) => ({
                    Component: m.DashboardPage,
                  })),
              },
              {
                path: '/mais',
                lazy: () => import('@/app/MorePage').then((m) => ({ Component: m.MorePage })),
              },
              {
                path: '/mais/contas',
                lazy: () =>
                  import('@/features/accounts/AccountsPage').then((m) => ({
                    Component: m.AccountsPage,
                  })),
              },
              {
                path: '/mais/familia',
                lazy: () =>
                  import('@/features/members/FamilyPage').then((m) => ({
                    Component: m.FamilyPage,
                  })),
              },
              {
                path: '/mais/familia/convidar',
                lazy: () =>
                  import('@/features/members/InvitePage').then((m) => ({
                    Component: m.InvitePage,
                  })),
              },
              {
                path: '/mais/modulos',
                lazy: () =>
                  import('@/features/modules/ModulesPage').then((m) => ({
                    Component: m.ModulesPage,
                  })),
              },
              {
                path: '/mais/privacidade',
                lazy: () =>
                  import('@/features/privacy/PrivacyRoutes').then((m) => ({
                    Component: m.PrivacyPage,
                  })),
              },
              {
                path: '/mais/privacidade/atividade',
                lazy: () =>
                  import('@/features/privacy/PrivacyRoutes').then((m) => ({
                    Component: m.ActivityPage,
                  })),
              },
              {
                path: '/mais/categorias',
                lazy: () =>
                  import('@/features/categories/CategoriesPage').then((m) => ({
                    Component: m.CategoriesPage,
                  })),
              },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]

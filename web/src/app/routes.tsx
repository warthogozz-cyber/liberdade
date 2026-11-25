import { Navigate, Outlet, type RouteObject } from 'react-router'
import { AppShell } from '@/app/AppShell'
import { ModuleRoute } from '@/app/ModuleRoute'
import { NotFoundPage } from '@/app/NotFoundPage'
import { RootRedirect } from '@/app/RootRedirect'
import { SessionLayout } from '@/app/SessionLayout'
import { WorkspaceLayout } from '@/app/WorkspaceLayout'
import { WelcomePage } from '@/features/onboarding/WelcomePage'
// Registers the planning module and its messages; the screens load on demand.
import '@/features/gamification/register'
import '@/features/indicators/register'
import '@/features/planning/register'
import '@/features/recurrences/register'
import '@/features/ventures/register'

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
    // Public: read before there is an account, and from the acceptance screen's new tab.
    path: '/privacidade',
    lazy: () =>
      import('@/features/legal/PrivacyPolicyPage').then((m) => ({
        Component: m.PrivacyPolicyPage,
      })),
  },
  {
    path: '/termos',
    lazy: () => import('@/features/legal/TermsPage').then((m) => ({ Component: m.TermsPage })),
  },
  {
    // Public: the address in the indicator robot's User-Agent. Not in More.
    path: '/robo',
    lazy: () => import('@/features/legal/BotPage').then((m) => ({ Component: m.BotPage })),
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
                path: '/lancamentos/importar',
                lazy: () =>
                  import('@/features/import/ImportPage').then((m) => ({
                    Component: m.ImportPage,
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
                path: '/painel/retrato',
                lazy: () =>
                  import('@/features/portrait/PortraitPage').then((m) => ({
                    Component: m.PortraitPage,
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
                path: '/mais/plano',
                lazy: () =>
                  import('@/features/billing/PlanPage').then((m) => ({ Component: m.PlanPage })),
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
                path: '/mais/ajustes',
                lazy: () =>
                  import('@/features/settings/SettingsPage').then((m) => ({
                    Component: m.SettingsPage,
                  })),
              },
              {
                path: '/mais/categorias',
                lazy: () =>
                  import('@/features/categories/CategoriesPage').then((m) => ({
                    Component: m.CategoriesPage,
                  })),
              },
              {
                // With the indicators module off, its screens do not open either.
                element: (
                  <ModuleRoute module="indicators">
                    <Outlet />
                  </ModuleRoute>
                ),
                children: [
                  {
                    path: '/mais/indicadores',
                    lazy: () =>
                      import('@/features/indicators/IndicatorsPage').then((m) => ({
                        Component: m.IndicatorsPage,
                      })),
                  },
                  {
                    path: '/mais/indicadores/calculadora',
                    lazy: () =>
                      import('@/features/indicators/RealReturnPage').then((m) => ({
                        Component: m.RealReturnPage,
                      })),
                  },
                ],
              },
              {
                // With the gamification module off, its screen does not open either.
                element: (
                  <ModuleRoute module="gamification">
                    <Outlet />
                  </ModuleRoute>
                ),
                children: [
                  {
                    path: '/mais/conquistas',
                    lazy: () =>
                      import('@/features/gamification/AchievementsPage').then((m) => ({
                        Component: m.AchievementsPage,
                      })),
                  },
                ],
              },
              {
                // With the projects module off, its screens do not open either; a Youth who
                // types the address goes back to More. The other screens join as they are built.
                element: (
                  <ModuleRoute module="ventures">
                    <Outlet />
                  </ModuleRoute>
                ),
                children: [
                  {
                    lazy: () =>
                      import('@/app/NoYouthRoute').then((m) => ({ Component: m.NoYouthRoute })),
                    children: [
                      {
                        path: '/mais/projetos',
                        lazy: () =>
                          import('@/features/ventures/VenturesListPage').then((m) => ({
                            Component: m.VenturesListPage,
                          })),
                      },
                      {
                        path: '/mais/projetos/comecar',
                        lazy: () =>
                          import('@/features/ventures/StartPage').then((m) => ({
                            Component: m.StartPage,
                          })),
                      },
                      {
                        path: '/mais/projetos/:id/editar',
                        lazy: () =>
                          import('@/features/ventures/VentureEditPage').then((m) => ({
                            Component: m.VentureEditPage,
                          })),
                      },
                    ],
                  },
                ],
              },
              {
                path: '/mais/recorrencias',
                element: <Navigate to="/planejar/recorrencias" replace />,
              },
              {
                // With the planning module off, none of its screens opens (US-27).
                element: (
                  <ModuleRoute module="planning">
                    <Outlet />
                  </ModuleRoute>
                ),
                children: [
                  {
                    path: '/planejar',
                    lazy: () =>
                      import('@/features/planning/PlanHub').then((m) => ({ Component: m.PlanHub })),
                  },
                  {
                    // The budget and the reserve are not a Youth's to see (US-10).
                    lazy: () =>
                      import('@/features/planning/AdultsOnlyRoute').then((m) => ({
                        Component: m.AdultsOnlyRoute,
                      })),
                    children: [
                      {
                        path: '/planejar/orcamento',
                        lazy: () =>
                          import('@/features/planning/BudgetPage').then((m) => ({
                            Component: m.BudgetPage,
                          })),
                      },
                      {
                        path: '/planejar/reserva',
                        lazy: () =>
                          import('@/features/planning/ReservePage').then((m) => ({
                            Component: m.ReservePage,
                          })),
                      },
                    ],
                  },
                  {
                    path: '/planejar/metas',
                    lazy: () =>
                      import('@/features/planning/GoalsPage').then((m) => ({
                        Component: m.GoalsPage,
                      })),
                  },
                  {
                    path: '/planejar/recorrencias',
                    lazy: () =>
                      import('@/features/recurrences/RecurrencesPage').then((m) => ({
                        Component: m.RecurrencesPage,
                      })),
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]

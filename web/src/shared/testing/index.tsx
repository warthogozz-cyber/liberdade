import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { render, renderHook } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { IntlProvider } from 'react-intl'
import { createMemoryRouter, type RouteObject } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { createQueryClient } from '@/shared/api'
import { locale, useAppMessages } from '@/shared/i18n'
import { ToastProvider } from '@/shared/ui'

export { outboxItem, outboxPayload, transactionView } from './outbox'

/** The app's QueryClient without retries, so a failed call fails the test at once. */
export function createTestQueryClient(): QueryClient {
  const queryClient = createQueryClient(() => {})
  const defaults = queryClient.getDefaultOptions()
  queryClient.setDefaultOptions({ ...defaults, queries: { ...defaults.queries, retry: false } })
  return queryClient
}

/** The app's providers, without saving the cache to the device. */
function wrapperFor(queryClient: QueryClient) {
  return function Providers({ children }: { children: ReactNode }) {
    return (
      <IntlProvider locale={locale} messages={useAppMessages()}>
        <QueryClientProvider client={queryClient}>
          <ToastProvider>{children}</ToastProvider>
        </QueryClientProvider>
      </IntlProvider>
    )
  }
}

interface RenderOptions {
  /** The URL the router starts at; `path` by default. */
  route?: string
  /** The route pattern `ui` answers on; `/` by default. Any other URL renders nothing. */
  path?: string
  queryClient?: QueryClient
  /** The app's own route objects, instead of `ui` at `path`: needed for lazy routes. */
  routes?: RouteObject[]
}

/** Renders `ui` inside the app's providers and a memory router. */
export function renderWithProviders(
  ui: ReactElement | null,
  { route, path = '/', queryClient = createTestQueryClient(), routes }: RenderOptions = {},
) {
  const router = createMemoryRouter(
    routes ?? [
      { path, element: ui },
      { path: '*', element: null },
    ],
    { initialEntries: [route ?? path] },
  )
  const result = render(<RouterProvider router={router} />, { wrapper: wrapperFor(queryClient) })
  return { ...result, router, queryClient }
}

/** Renders a hook inside the app's providers. */
export function renderHookWithProviders<Result>(
  hook: () => Result,
  { queryClient = createTestQueryClient() }: { queryClient?: QueryClient } = {},
) {
  const result = renderHook(hook, { wrapper: wrapperFor(queryClient) })
  return { ...result, queryClient }
}

import '@fontsource-variable/inter'
import './index.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { AppProviders } from '@/app/providers'
import { routes } from '@/app/routes'
import { createQueryClient } from '@/shared/api'
import { clearSession } from '@/shared/offline'

const router = createBrowserRouter(routes)

let signingOut = false

// On a 401, leave the screens first so none fetches `/v1/me` again mid-cleanup, then clear the
// session. Another 401 while that runs does nothing.
const queryClient = createQueryClient(() => {
  if (signingOut) return
  signingOut = true
  void router
    .navigate('/entrar')
    .then(() => clearSession(queryClient))
    .finally(() => {
      signingOut = false
    })
})

// Vite inlines the variable at build time, so this branch and the mocks leave the normal build.
if (import.meta.env.VITE_API_MOCKS === 'true') {
  const { worker } = await import('@/mocks/browser')
  // MSW 3 renamed `onUnhandledRequest` to `onUnhandledFrame`.
  await worker.start({ onUnhandledFrame: 'bypass' })
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>
  </StrictMode>,
)

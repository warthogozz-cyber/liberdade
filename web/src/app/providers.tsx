import type { QueryClient } from '@tanstack/react-query'
import { PersistQueryClientProvider } from '@tanstack/react-query-persist-client'
import type { ReactNode } from 'react'
import { IntlProvider } from 'react-intl'
import { locale, messages } from '@/shared/i18n'
import { persistOptions } from '@/shared/offline'
import { ToastProvider } from '@/shared/ui'

interface AppProvidersProps {
  children: ReactNode
  queryClient: QueryClient
}

/**
 * The app's providers: the messages, then the query cache, saved to the device, then the notices,
 * above the router so a notice outlives the screen that showed it.
 */
export function AppProviders({ children, queryClient }: AppProvidersProps) {
  return (
    <IntlProvider locale={locale} messages={messages}>
      <PersistQueryClientProvider client={queryClient} persistOptions={persistOptions}>
        <ToastProvider>{children}</ToastProvider>
      </PersistQueryClientProvider>
    </IntlProvider>
  )
}

import type { QueryClient } from '@tanstack/react-query'
import { PersistQueryClientProvider } from '@tanstack/react-query-persist-client'
import type { ReactNode } from 'react'
import { IntlProvider } from 'react-intl'
import { ReauthProvider } from '@/shared/auth/ReauthProvider'
import { useLocale } from '@/shared/api'
import { useAppMessages } from '@/shared/i18n'
import { persistOptions } from '@/shared/offline'
import { ToastProvider } from '@/shared/ui'

interface AppProvidersProps {
  children: ReactNode
  queryClient: QueryClient
}

/** The messages, in the current family's locale; it reads the family, so it sits inside the cache. */
function FamilyIntl({ children }: { children: ReactNode }) {
  return (
    <IntlProvider locale={useLocale()} messages={useAppMessages()}>
      {children}
    </IntlProvider>
  )
}

/**
 * The app's providers: the query cache, saved to the device, then the messages (in the family's
 * locale, so they sit inside the cache), then the notices, above
 * the router so a notice outlives the screen that showed it.
 *
 * The device saves the cache at most once a second, so what a reload restores can predate the last
 * change (a family just created, an expense just sent) while still looking fresh. Every restored
 * query is fetched again; offline, the restored data stays on screen. The promise is not returned:
 * the provider would hold `isRestoring`, and so every screen, until the fetches settle. The routes
 * decide on the restored `me` before that fetch answers, so the new family and its data are also
 * saved at once (`saveQueryCacheNow`).
 */
export function AppProviders({ children, queryClient }: AppProvidersProps) {
  return (
    <PersistQueryClientProvider
      client={queryClient}
      persistOptions={persistOptions}
      onSuccess={() => void queryClient.invalidateQueries()}
    >
      <FamilyIntl>
        <ToastProvider>
          <ReauthProvider>{children}</ReauthProvider>
        </ToastProvider>
      </FamilyIntl>
    </PersistQueryClientProvider>
  )
}

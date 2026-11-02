import type { ReactNode } from 'react'
import { IntlProvider } from 'react-intl'
import { locale, messages } from '@/shared/i18n'

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <IntlProvider locale={locale} messages={messages}>
      {children}
    </IntlProvider>
  )
}

import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SignInPage } from '@/features/auth/SignInPage'
import { IntlProvider } from 'react-intl'
import { locale, messages } from '@/shared/i18n'

describe('SignInPage', () => {
  it('shows the app name and tagline', () => {
    render(
      <IntlProvider locale={locale} messages={messages}>
        <SignInPage />
      </IntlProvider>,
    )

    expect(screen.getByRole('heading', { level: 1, name: 'Liberdade' })).toBeInTheDocument()
    expect(screen.getByText('As finanças da sua família, simples e em paz.')).toBeInTheDocument()
  })
})

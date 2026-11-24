import { cleanup, render, screen } from '@testing-library/react'
import { IntlProvider } from 'react-intl'
import { afterEach, describe, expect, it } from 'vitest'
import { appMessages, locale } from '@/shared/i18n'
import { SourceStamp } from '@/shared/ui/SourceStamp'

afterEach(cleanup)

function renderStamp(props: Partial<Parameters<typeof SourceStamp>[0]> = {}) {
  render(
    <IntlProvider locale={locale} messages={appMessages()}>
      <SourceStamp
        source="Banco Central do Brasil"
        url={null}
        updatedAt="2026-11-25T02:30:00Z"
        stale={false}
        {...props}
      />
    </IntlProvider>,
  )
}

describe('SourceStamp', () => {
  it('showsSourceAndSaoPauloDate', () => {
    renderStamp()
    expect(
      screen.getByText('Fonte: Banco Central do Brasil · atualizado em 24/11/2026'),
    ).toBeTruthy()
  })

  it('neverUpdated', () => {
    renderStamp({ updatedAt: null })
    expect(screen.getByText(/ainda sem atualização/)).toBeTruthy()
  })

  it('staleBadgeIsText', () => {
    renderStamp({ stale: true })
    expect(screen.getByText('desatualizado')).toBeTruthy()
  })

  it('hidesTheBadgeWhenFresh', () => {
    renderStamp()
    expect(screen.queryByText('desatualizado')).toBeNull()
  })

  it('linksOnlyHttps', () => {
    renderStamp({ url: 'https://api.bcb.gov.br/x' })
    const link = screen.getByRole('link', { name: 'Banco Central do Brasil' })
    expect(link.getAttribute('target')).toBe('_blank')
    expect(link.getAttribute('rel')).toBe('noopener noreferrer')
    cleanup()
    renderStamp({ url: 'fake:CDI' })
    expect(screen.queryByRole('link')).toBeNull()
    expect(screen.getByText(/Banco Central do Brasil/)).toBeTruthy()
  })
})

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { IntlProvider } from 'react-intl'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { locale, useAppMessages } from '@/shared/i18n'
import { MapEmbed } from './MapEmbed'
import { readLocation, writeLocation } from './locationStore'

const USER = 'u-map-test'

function Wrapped(props: Parameters<typeof MapEmbed>[0]) {
  return (
    <IntlProvider locale={locale} messages={useAppMessages()}>
      <MapEmbed {...props} />
    </IntlProvider>
  )
}

const show = (userId: string | undefined = USER) =>
  render(<Wrapped userId={userId} name="Mercado Exemplo" lat="-10.00012" lon="-50.00034" />)

beforeEach(() => {
  vi.stubEnv('VITE_GOOGLE_MAPS_EMBED_KEY', 'chave-de-teste')
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.unstubAllEnvs()
})

describe('MapEmbed', () => {
  it('noticeThenIframe', () => {
    show()
    expect(document.querySelector('iframe')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Ver no mapa' }))
    expect(
      screen.getByText(/O mapa é do Google\. O Google vai saber que você abriu o mapa/),
    ).toBeVisible()
    expect(document.querySelector('iframe')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Abrir mapa' }))
    const frame = document.querySelector('iframe') as HTMLIFrameElement
    expect(frame.getAttribute('src')).toBe(
      'https://www.google.com/maps/embed/v1/place?key=chave-de-teste&q=-10.00012,-50.00034',
    )
    expect(frame.getAttribute('referrerpolicy')).toBe('strict-origin-when-cross-origin')
    expect(frame.getAttribute('loading')).toBe('lazy')
    expect(frame.getAttribute('title')).toBe('Mapa de Mercado Exemplo')
  })

  it('notNowKeepsNothing', () => {
    show()
    fireEvent.click(screen.getByRole('button', { name: 'Ver no mapa' }))
    fireEvent.click(screen.getByRole('button', { name: 'Agora não' }))

    expect(document.querySelector('iframe')).toBeNull()
    expect(readLocation(USER)).toBeNull()
  })

  it('dontAskAgainRemembers', () => {
    writeLocation(USER, { mode: 'approximate' })
    show()
    fireEvent.click(screen.getByRole('button', { name: 'Ver no mapa' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Não perguntar de novo' }))
    fireEvent.click(screen.getByRole('button', { name: 'Abrir mapa' }))
    expect(readLocation(USER)?.mapConsent).toBe(true)

    fireEvent.click(screen.getByRole('button', { name: 'Esconder o mapa' }))
    expect(document.querySelector('iframe')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Ver no mapa' }))

    expect(screen.queryByRole('button', { name: 'Abrir mapa' })).not.toBeInTheDocument()
    expect(document.querySelector('iframe')).not.toBeNull()
  })

  it('withoutAStoredChoiceTheConsentLastsOnlyForTheScreen', () => {
    show()
    fireEvent.click(screen.getByRole('button', { name: 'Ver no mapa' }))
    fireEvent.click(screen.getByRole('checkbox', { name: 'Não perguntar de novo' }))
    fireEvent.click(screen.getByRole('button', { name: 'Abrir mapa' }))
    // No choice was stored, so none is invented.
    expect(readLocation(USER)).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Esconder o mapa' }))
    fireEvent.click(screen.getByRole('button', { name: 'Ver no mapa' }))
    expect(document.querySelector('iframe')).not.toBeNull()
  })

  it('noKeyNoButton', () => {
    vi.stubEnv('VITE_GOOGLE_MAPS_EMBED_KEY', '')
    show()
    expect(screen.queryByRole('button', { name: 'Ver no mapa' })).not.toBeInTheDocument()
  })

  it('showsTheMarketNotThePerson', () => {
    // The person's own position is known to the device, and still never reaches the map.
    Object.defineProperty(navigator, 'geolocation', {
      value: { getCurrentPosition: vi.fn() },
      configurable: true,
    })
    writeLocation(USER, { mode: 'typed', point: { lat: '-20.111', lon: '-40.222' } })
    show()
    fireEvent.click(screen.getByRole('button', { name: 'Ver no mapa' }))
    fireEvent.click(screen.getByRole('button', { name: 'Abrir mapa' }))

    const src = (document.querySelector('iframe') as HTMLIFrameElement).getAttribute('src') ?? ''
    expect(new URL(src).searchParams.get('q')).toBe('-10.00012,-50.00034')
    expect(src).not.toContain('-20.111')
  })
})

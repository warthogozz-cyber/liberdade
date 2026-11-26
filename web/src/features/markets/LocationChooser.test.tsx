import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import type { ComponentProps } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { LocationChooser } from './LocationChooser'
import { readLocation } from './locationStore'

const user = '0192f3a4-0000-7000-8000-000000000001'
const getCurrentPosition = vi.fn()

beforeEach(() => {
  getCurrentPosition.mockReset()
  Object.defineProperty(navigator, 'geolocation', {
    value: { getCurrentPosition },
    configurable: true,
  })
})

afterEach(() => {
  cleanup()
  localStorage.clear()
})

function renderChooser(props: Partial<ComponentProps<typeof LocationChooser>> = {}) {
  const onChosen = vi.fn()
  const workspace = seedMockFamily()
  renderWithProviders(
    <LocationChooser
      open
      onOpenChange={() => {}}
      userId={user}
      workspaceId={workspace.id}
      current={null}
      geocodingEnabled
      homeAvailable={false}
      onChosen={onChosen}
      {...props}
    />,
  )
  return onChosen
}

const answerWith = (latitude: number, longitude: number) =>
  getCurrentPosition.mockImplementation((success: PositionCallback) =>
    success({ coords: { latitude, longitude } } as GeolocationPosition),
  )

describe('LocationChooser', () => {
  it('asksPermissionOnlyAfterContinue', async () => {
    answerWith(-10.12345, -50.54321)
    renderChooser()

    expect(getCurrentPosition).not.toHaveBeenCalled()
    fireEvent.click(await screen.findByRole('radio', { name: /Minha localização precisa/ }))
    expect(getCurrentPosition).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))

    await waitFor(() => expect(getCurrentPosition).toHaveBeenCalledTimes(1))
  })

  it('preciseKeepsTheReadingInMemoryOnly', async () => {
    answerWith(-10.12345, -50.54321)
    const onChosen = renderChooser()

    fireEvent.click(await screen.findByRole('radio', { name: /Minha localização precisa/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))

    await waitFor(() => expect(onChosen).toHaveBeenCalled())
    expect(onChosen).toHaveBeenCalledWith({ mode: 'precise' }, { lat: -10.12345, lon: -50.54321 })
    expect(readLocation(user)).toEqual({ mode: 'precise' })
  })

  it('approximateRoundsBeforeStoring', async () => {
    answerWith(-10.12345, -50.54321)
    renderChooser()

    fireEvent.click(await screen.findByRole('radio', { name: /Minha localização aproximada/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))

    await waitFor(() => expect(readLocation(user)?.mode).toBe('approximate'))
    expect(readLocation(user)?.point).toEqual({ lat: '-10.12', lon: '-50.54' })
    expect(localStorage.getItem(`liberdade.markets.${user}`)).not.toContain('10.12345')
  })

  it('hidesTypedWhenGeocodingOff', async () => {
    renderChooser({ geocodingEnabled: false })

    expect(await screen.findByRole('radio', { name: /Minha localização precisa/ })).toBeVisible()
    expect(screen.queryByRole('radio', { name: /Digitar um endereço/ })).not.toBeInTheDocument()
  })

  it('showsTypedWhenGeocodingOn', async () => {
    renderChooser()

    expect(await screen.findByRole('radio', { name: /Digitar um endereço/ })).toBeVisible()
  })

  it('hidesTheFamilyAddressWhenThereIsNone', async () => {
    renderChooser({ homeAvailable: false })

    expect(await screen.findByRole('radio', { name: /Minha localização precisa/ })).toBeVisible()
    expect(screen.queryByRole('radio', { name: /Endereço da família/ })).not.toBeInTheDocument()
  })

  it('deniedPermissionShowsAMessageAndStaysOpen', async () => {
    getCurrentPosition.mockImplementation(
      (_success: PositionCallback, failure: PositionErrorCallback) =>
        failure({ code: 1 } as GeolocationPositionError),
    )
    const onChosen = renderChooser()

    fireEvent.click(await screen.findByRole('radio', { name: /Minha localização precisa/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/permiss/i)
    expect(onChosen).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Continuar' })).toBeVisible()
    expect(readLocation(user)).toBeNull()
  })
})

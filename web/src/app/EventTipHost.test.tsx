import { act, cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { EventTipHost } from '@/app/EventTipHost'
import { mockState } from '@/mocks/data'
import { hasSeen, isHelpOpen, setHelpOpen } from '@/shared/help'
import { markRemoteArrival, resetLocalChanges } from '@/shared/live/localChanges'
import { notifyQueued } from '@/shared/offline/queuedSignal'
import { renderWithProviders } from '@/shared/testing'

const OFFLINE = 'Sem internet, seus gastos ficam guardados neste aparelho.'
const LIVE = 'Um lançamento chegou de outro aparelho.'

const setOnline = (online: boolean) => vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(online)

/** Renders the host and waits until it knows the person, so the events are not missed. */
async function renderHost() {
  const result = renderWithProviders(<EventTipHost />)
  // The listeners start once the person is known; a first event that lands before is lost.
  await act(() => new Promise((resolve) => setTimeout(resolve, 50)))
  return result
}

const foreign: HTMLElement[] = []
function addForeign(role: string) {
  const el = document.createElement('div')
  el.setAttribute('role', role)
  document.body.appendChild(el)
  foreign.push(el)
}

beforeEach(() => {
  localStorage.removeItem('liberdade.tips')
  setOnline(true)
})

afterEach(() => {
  cleanup()
  for (const el of foreign.splice(0)) el.remove()
  resetLocalChanges()
  vi.restoreAllMocks()
})

describe('EventTipHost', () => {
  it('offlineTipOnce', async () => {
    setOnline(false)
    await renderHost()
    act(() => notifyQueued())

    await screen.findByText(new RegExp(OFFLINE))
    expect(screen.getByRole('status')).toHaveTextContent(OFFLINE)
    fireEvent.click(screen.getByRole('button', { name: 'Entendi' }))
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    expect(hasSeen(mockState.user.id, 'event-offline')).toBe(true)

    act(() => notifyQueued())
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })

  it('noOfflineTipWhenOnline', async () => {
    await renderHost()
    act(() => notifyQueued())
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })

  it('liveTipOnArrival', async () => {
    await renderHost()
    act(() => markRemoteArrival('t1'))

    await screen.findByText(new RegExp(LIVE))
    expect(screen.getByRole('status')).toHaveTextContent(LIVE)
  })

  it('skipsWhileDialogOpen', async () => {
    await renderHost()
    addForeign('dialog')
    act(() => markRemoteArrival('t1'))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    expect(hasSeen(mockState.user.id, 'event-live')).toBe(false)
  })

  it('skipsWhileAlertDialogOpen', async () => {
    await renderHost()
    addForeign('alertdialog')
    act(() => markRemoteArrival('t1'))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    expect(hasSeen(mockState.user.id, 'event-live')).toBe(false)
  })

  it('skipsWhileTourOpen', async () => {
    await renderHost()
    setHelpOpen(true)
    act(() => markRemoteArrival('t1'))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })

  it('oneAtATime', async () => {
    setOnline(false)
    await renderHost()
    act(() => notifyQueued())
    await screen.findByText(new RegExp(OFFLINE))
    expect(screen.getByRole('status')).toHaveTextContent(OFFLINE)
    act(() => markRemoteArrival('t1'))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status')).toHaveTextContent(OFFLINE)
    expect(hasSeen(mockState.user.id, 'event-live')).toBe(false)
  })

  it('tipsOffShowsNothing', async () => {
    localStorage.setItem('liberdade.tips', 'off')
    await renderHost()
    act(() => markRemoteArrival('t1'))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    await waitFor(() => expect(hasSeen(mockState.user.id, 'event-live')).toBe(false))
  })

  it('liveRegionExistsBeforeText', async () => {
    await renderHost()
    const region = screen.getByRole('status')
    expect(region).toBeEmptyDOMElement()

    act(() => markRemoteArrival('t1'))
    await screen.findByText(new RegExp(LIVE))

    expect(screen.getByRole('status')).toBe(region)
    expect(region).toHaveTextContent(LIVE)
  })

  it('marksHelpOpenAsSoonAsAccepted', async () => {
    setOnline(false)
    await renderHost()
    act(() => notifyQueued())
    expect(isHelpOpen()).toBe(true)

    fireEvent.click(await screen.findByRole('button', { name: 'Entendi' }))
    expect(isHelpOpen()).toBe(false)
  })

  it('unmountWhileShowingReleasesHelpSlot', async () => {
    const view = await renderHost()
    act(() => markRemoteArrival('t1'))
    await screen.findByText(new RegExp(LIVE))
    expect(isHelpOpen()).toBe(true)

    view.unmount()
    expect(isHelpOpen()).toBe(false)
  })
})

import { act, cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { EventTipHost } from '@/app/EventTipHost'
import { mockState } from '@/mocks/data'
import { Outlet } from 'react-router'
import { hasSeen, isHelpOpen, PageTip, PageTour, setHelpOpen } from '@/shared/help'
import { markRemoteArrival, resetLocalChanges } from '@/shared/live/localChanges'
import { notifyQueued } from '@/shared/offline/queuedSignal'
import { queryKeys } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { useToast } from '@/shared/ui'

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

/** The host's live region: the one polite, atomic region on the page. */
const region = () => {
  const el = document.querySelector<HTMLElement>('[aria-live="polite"][aria-atomic="true"]')
  if (!el) throw new Error('The live region is missing')
  return el
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
    expect(region()).toHaveTextContent(OFFLINE)
    fireEvent.click(screen.getByRole('button', { name: 'Entendi' }))
    expect(region()).toBeEmptyDOMElement()
    expect(hasSeen(mockState.user.id, 'event-offline')).toBe(true)

    act(() => notifyQueued())
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))
    expect(region()).toBeEmptyDOMElement()
  })

  it('noOfflineTipWhenOnline', async () => {
    await renderHost()
    act(() => notifyQueued())
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(region()).toBeEmptyDOMElement()
  })

  it('liveTipOnArrival', async () => {
    await renderHost()
    act(() => markRemoteArrival('t1'))

    await screen.findByText(new RegExp(LIVE))
    expect(region()).toHaveTextContent(LIVE)
  })

  it('skipsWhileDialogOpen', async () => {
    await renderHost()
    addForeign('dialog')
    act(() => markRemoteArrival('t1'))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(region()).toBeEmptyDOMElement()
    expect(hasSeen(mockState.user.id, 'event-live')).toBe(false)
  })

  it('skipsWhileAlertDialogOpen', async () => {
    await renderHost()
    addForeign('alertdialog')
    act(() => markRemoteArrival('t1'))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(region()).toBeEmptyDOMElement()
    expect(hasSeen(mockState.user.id, 'event-live')).toBe(false)
  })

  it('skipsWhileTourOpen', async () => {
    await renderHost()
    setHelpOpen(true)
    act(() => markRemoteArrival('t1'))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(region()).toBeEmptyDOMElement()
  })

  it('oneAtATime', async () => {
    setOnline(false)
    await renderHost()
    act(() => notifyQueued())
    await screen.findByText(new RegExp(OFFLINE))
    expect(region()).toHaveTextContent(OFFLINE)
    act(() => markRemoteArrival('t1'))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(region().querySelectorAll('button')).toHaveLength(1)
    expect(region()).toHaveTextContent(OFFLINE)
    expect(hasSeen(mockState.user.id, 'event-live')).toBe(false)
  })

  it('tipsOffShowsNothing', async () => {
    localStorage.setItem('liberdade.tips', 'off')
    await renderHost()
    act(() => markRemoteArrival('t1'))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(region()).toBeEmptyDOMElement()
    await waitFor(() => expect(hasSeen(mockState.user.id, 'event-live')).toBe(false))
  })

  it('liveRegionExistsBeforeText', async () => {
    await renderHost()
    const before = region()
    expect(before).toBeEmptyDOMElement()
    expect(before).toHaveAttribute('aria-live', 'polite')

    act(() => markRemoteArrival('t1'))
    await screen.findByText(new RegExp(LIVE))

    expect(region()).toBe(before)
    expect(before).toHaveTextContent(LIVE)
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

describe('EventTipHost edge cases', () => {
  it('anotherPersonDropsTheTip', async () => {
    const view = await renderHost()
    act(() => markRemoteArrival('t1'))
    await screen.findByText(new RegExp(LIVE))

    // Another person signs in on this tab without a reload (as accepting an invite can do).
    const me = view.queryClient.getQueryData<{ user: { id: string } }>(queryKeys.me)!
    act(() =>
      view.queryClient.setQueryData(queryKeys.me, { ...me, user: { ...me.user, id: 'other' } }),
    )
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(region()).toBeEmptyDOMElement()
    expect(isHelpOpen()).toBe(false)
  })

  it('tipHidesWhileALaterDialogIsOpen', async () => {
    await renderHost()
    act(() => markRemoteArrival('t1'))
    await screen.findByText(new RegExp(LIVE))

    act(() => addForeign('dialog'))
    await waitFor(() => expect(region()).toBeEmptyDOMElement())
    expect(hasSeen(mockState.user.id, 'event-live')).toBe(false)

    act(() => foreign.splice(0).forEach((el) => el.remove()))
    expect(await screen.findByText(new RegExp(LIVE))).toBeInTheDocument()
  })
})

describe('EventTipHost with a page tour or tip', () => {
  const TIP = 'Todos os lançamentos do mês. Toque em um para editar ou excluir.'

  /** The host in a layout, as in WorkspaceLayout, with a tour screen and a tip screen under it. */
  async function renderLayout() {
    Element.prototype.scrollIntoView = vi.fn()
    const result = renderWithProviders(null, {
      routes: [
        {
          path: '/',
          element: (
            <>
              <Outlet />
              <EventTipHost />
            </>
          ),
          children: [
            { index: true, element: null },
            {
              path: 'planejar',
              element: (
                <>
                  <div data-tour="planning-budget" />
                  <div data-tour="planning-goals" />
                  <PageTour id="planning" />
                </>
              ),
            },
            { path: 'lancamentos', element: <PageTip id="transactions" /> },
          ],
        },
      ],
    })
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))
    return result
  }

  it('tourWaitsForEventTip', async () => {
    setOnline(false)
    const { router } = await renderLayout()
    act(() => notifyQueued())
    await screen.findByText(new RegExp(OFFLINE))

    await act(() => router.navigate('/planejar'))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))
    expect(screen.queryByText('Passo 1 de 2')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Entendi' }))
    expect(await screen.findByText('Passo 1 de 2')).toBeInTheDocument()
    // The tour holds the slot itself, which must not close it.
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))
    expect(screen.getByText('Passo 1 de 2')).toBeInTheDocument()
  })

  it('pageTipWaitsForEventTip', async () => {
    setOnline(false)
    const { router } = await renderLayout()
    act(() => notifyQueued())
    await screen.findByText(new RegExp(OFFLINE))

    await act(() => router.navigate('/lancamentos'))
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))
    expect(screen.queryByText(TIP)).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Entendi' }))
    expect(await screen.findByText(TIP)).toBeInTheDocument()
    // The bubble holds the slot itself, which must not hide it.
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))
    expect(screen.getByText(TIP)).toBeInTheDocument()
  })
})

describe('EventTipHost with the undo toast', () => {
  type Show = ReturnType<typeof useToast>['show']
  let show: Show | undefined

  function Probe() {
    show = useToast().show
    return null
  }

  const undoToast = () =>
    act(() =>
      show!({
        message: 'Gasto salvo',
        action: { label: 'Desfazer', altText: 'Desfazer o último gasto', onClick: () => {} },
      }),
    )

  async function renderWithToast() {
    const result = renderWithProviders(
      <>
        <Probe />
        <EventTipHost />
      </>,
    )
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))
    return result
  }

  it('tipWaitsForTheToast', async () => {
    setOnline(false)
    await renderWithToast()
    // An offline save shows the undo toast and queues the change at the same moment.
    undoToast()
    act(() => notifyQueued())
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)))

    expect(screen.getByRole('button', { name: 'Desfazer' })).toBeVisible()
    expect(region()).toBeEmptyDOMElement()
    // The slot is taken, so no tour opens while the tip waits.
    expect(isHelpOpen()).toBe(true)

    fireEvent.click(screen.getByRole('button', { name: 'Desfazer' }))
    expect(await screen.findByText(new RegExp(OFFLINE))).toBeInTheDocument()
  })

  it('tipStepsAsideForALaterToast', async () => {
    setOnline(false)
    await renderWithToast()
    act(() => notifyQueued())
    await screen.findByText(new RegExp(OFFLINE))

    undoToast()
    expect(screen.getByRole('button', { name: 'Desfazer' })).toBeVisible()
    expect(region()).toBeEmptyDOMElement()
    expect(hasSeen(mockState.user.id, 'event-offline')).toBe(false)

    fireEvent.click(screen.getByRole('button', { name: 'Desfazer' }))
    expect(await screen.findByText(new RegExp(OFFLINE))).toBeInTheDocument()
  })
})

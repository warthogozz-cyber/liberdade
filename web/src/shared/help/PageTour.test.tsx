import { act, cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@/shared/testing'
import { PageTour } from './PageTour'

// The markets tour has no steps until its screen gets anchors (ruling R2), so these use planning.
const ANCHORS = ['planning-budget', 'planning-goals', 'planning-reserve', 'planning-recurring']
const SEEN_SUFFIX = ':tour-planning'

function addAnchors() {
  for (const name of ANCHORS) {
    const el = document.createElement('div')
    el.setAttribute('data-tour', name)
    document.body.appendChild(el)
  }
}

const seen = () =>
  Object.keys(localStorage).some((k) => k.startsWith('liberdade.tip:') && k.endsWith(SEEN_SUFFIX))

const wait = () => new Promise((resolve) => setTimeout(resolve, 50))

/** Renders the tour, then arrives at its route with `state`, as the "Ver o tour" link does. */
async function renderForced(state: unknown = { forceTour: 'planning' }) {
  const result = renderWithProviders(<PageTour id="planning" />, { path: '/planejar' })
  await act(() => result.router.navigate('/planejar', { state }))
  return result
}

beforeEach(() => {
  Element.prototype.scrollIntoView = vi.fn()
  addAnchors()
})

afterEach(() => {
  cleanup()
  document.body.querySelectorAll('[data-tour], [role="dialog"]').forEach((el) => el.remove())
})

describe('PageTour', () => {
  it('forcedOpensWhenTipsOff', async () => {
    // The test setup turns the tips off.
    expect(localStorage.getItem('liberdade.tips')).toBe('off')
    await renderForced()

    expect(await screen.findByRole('dialog')).toHaveTextContent('Passo 1 de 4')
  })

  it('forcedOpensWhenSeen', async () => {
    localStorage.removeItem('liberdade.tips')
    const first = renderWithProviders(<PageTour id="planning" />, { path: '/planejar' })
    fireEvent.click(await screen.findByRole('button', { name: 'Pular' }))
    expect(seen()).toBe(true)
    first.unmount()

    await renderForced()

    expect(await screen.findByRole('dialog')).toHaveTextContent('Passo 1 de 4')
  })

  it('otherForcedTourDoesNotOpen', async () => {
    await renderForced({ forceTour: 'markets' })

    await wait()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('closeClearsForceState', async () => {
    const { router } = await renderForced()
    fireEvent.click(await screen.findByRole('button', { name: 'Pular' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(seen()).toBe(true)
    await waitFor(() => expect(router.state.location.state).toBeNull())
    expect(router.state.historyAction).toBe('REPLACE')
    expect(router.state.location.pathname).toBe('/planejar')
  })

  it('waitsForDialog', async () => {
    localStorage.removeItem('liberdade.tips')
    const picker = document.createElement('div')
    picker.setAttribute('role', 'dialog')
    picker.setAttribute('aria-label', 'Onde procurar mercados?')
    document.body.appendChild(picker)
    renderWithProviders(<PageTour id="planning" />, { path: '/planejar' })

    await wait()
    expect(screen.queryByText('Passo 1 de 4')).not.toBeInTheDocument()

    act(() => picker.remove())
    expect(await screen.findByText('Passo 1 de 4')).toBeInTheDocument()

    // Once open, a dialog that opens later does not close the tour.
    const later = document.createElement('div')
    later.setAttribute('role', 'dialog')
    act(() => {
      document.body.appendChild(later)
    })
    await wait()
    expect(screen.getByText('Passo 1 de 4')).toBeInTheDocument()
  })
})

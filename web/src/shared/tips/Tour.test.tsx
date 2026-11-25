import { act, cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@/shared/testing'
import { PageTour } from './PageTour'

const scrollIntoView = vi.fn()

function anchors(names: string[]) {
  return names.map((name) => {
    const el = document.createElement('div')
    el.setAttribute('data-tour', name)
    document.body.appendChild(el)
    return el
  })
}

const ALL = ['planning-budget', 'planning-goals', 'planning-reserve', 'planning-recurring']

function setReducedMotion(reduced: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: reduced && query.includes('prefers-reduced-motion'),
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    onchange: null,
    dispatchEvent: () => false,
  }))
}

beforeEach(() => {
  localStorage.setItem('liberdade.tips', 'on')
  Element.prototype.scrollIntoView = scrollIntoView
  scrollIntoView.mockClear()
  setReducedMotion(false)
})

afterEach(() => {
  cleanup()
  document.body.querySelectorAll('[data-tour]').forEach((el) => el.remove())
})

const seen = () =>
  Object.keys(localStorage).some(
    (k) => k.startsWith('liberdade.tip:') && k.endsWith(':tour-planning'),
  )

const wait = () => new Promise((resolve) => setTimeout(resolve, 50))

describe('Tour', () => {
  it('opensOnTheFirstVisit', async () => {
    anchors(ALL)
    renderWithProviders(<PageTour id="planning" />)

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveAttribute('aria-modal', 'false')
    expect(dialog).toHaveTextContent('Passo 1 de 4')
    expect(dialog).toHaveTextContent('Defina um limite por categoria')
    await waitFor(() => expect(dialog).toHaveFocus())
    expect(screen.queryByRole('button', { name: 'Voltar' })).not.toBeInTheDocument()
  })

  it('nextAndFinish', async () => {
    anchors(ALL)
    const first = renderWithProviders(<PageTour id="planning" />)
    await screen.findByRole('dialog')
    for (let i = 0; i < 3; i++) fireEvent.click(screen.getByRole('button', { name: 'Próximo' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Passo 4 de 4')
    fireEvent.click(screen.getByRole('button', { name: 'Concluir' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(seen()).toBe(true)
    first.unmount()

    renderWithProviders(<PageTour id="planning" />)
    await wait()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('backFromStepTwo', async () => {
    anchors(ALL)
    renderWithProviders(<PageTour id="planning" />)
    await screen.findByRole('dialog')
    fireEvent.click(screen.getByRole('button', { name: 'Próximo' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Passo 2 de 4')
    fireEvent.click(screen.getByRole('button', { name: 'Voltar' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Passo 1 de 4')
  })

  it('skipOnAnyStep', async () => {
    anchors(ALL)
    renderWithProviders(<PageTour id="planning" />)
    await screen.findByRole('dialog')
    fireEvent.click(screen.getByRole('button', { name: 'Próximo' }))
    fireEvent.click(screen.getByRole('button', { name: 'Pular' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(seen()).toBe(true)
  })

  it('escapeSkips', async () => {
    anchors(ALL)
    renderWithProviders(<PageTour id="planning" />)
    await screen.findByRole('dialog')
    fireEvent.keyDown(document, { key: 'Escape' })

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(seen()).toBe(true)
  })

  it('escapeInsideAnotherDialogDoesNotSkip', async () => {
    anchors(ALL)
    const other = document.createElement('div')
    other.setAttribute('role', 'dialog')
    const inner = document.createElement('button')
    other.appendChild(inner)
    document.body.appendChild(other)
    renderWithProviders(<PageTour id="planning" />)
    await screen.findByRole('dialog', { name: /limite por categoria/ })
    fireEvent.keyDown(inner, { key: 'Escape' })

    expect(screen.getByRole('dialog', { name: /limite por categoria/ })).toBeInTheDocument()
    expect(seen()).toBe(false)
    other.remove()
  })

  it('focusGoesBack', async () => {
    anchors(ALL)
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()
    renderWithProviders(<PageTour id="planning" />)
    const dialog = await screen.findByRole('dialog')
    await waitFor(() => expect(dialog).toHaveFocus())
    fireEvent.click(screen.getByRole('button', { name: 'Pular' }))

    expect(trigger).toHaveFocus()
    trigger.remove()
  })

  it('missingAnchorIsSkipped', async () => {
    anchors(['planning-budget', 'planning-goals', 'planning-recurring'])
    renderWithProviders(<PageTour id="planning" />)

    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Passo 1 de 3')
    fireEvent.click(screen.getByRole('button', { name: 'Próximo' }))
    fireEvent.click(screen.getByRole('button', { name: 'Próximo' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Passo 3 de 3')
    expect(screen.getByRole('dialog')).toHaveTextContent('Contas que se repetem')
  })

  it('lateAnchorOpensAndGrowsWithoutJumping', async () => {
    renderWithProviders(<PageTour id="planning" />)
    await wait()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(seen()).toBe(false)

    act(() => {
      anchors(['planning-goals'])
    })
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Passo 1 de 1')

    act(() => {
      anchors(['planning-budget', 'planning-recurring'])
    })
    await waitFor(() => expect(screen.getByRole('dialog')).toHaveTextContent('Passo 2 de 3'))
    expect(screen.getByRole('dialog')).toHaveTextContent('Metas guardam dinheiro')
  })

  it('doesNotOpenAndIsNotMarkedWithoutAnchors', async () => {
    renderWithProviders(<PageTour id="planning" />)
    await wait()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(seen()).toBe(false)
  })

  it('offDoesNotOpen', async () => {
    localStorage.setItem('liberdade.tips', 'off')
    anchors(ALL)
    renderWithProviders(<PageTour id="planning" />)

    await wait()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('highlightsTheAnchorAndClearsIt', async () => {
    const [budget] = anchors(ALL)
    renderWithProviders(<PageTour id="planning" />)
    await screen.findByRole('dialog')
    expect(budget?.style.outline).not.toBe('')
    fireEvent.click(screen.getByRole('button', { name: 'Próximo' }))
    expect(budget?.style.outline).toBe('')
  })

  it('scrollsSmoothlyByDefault', async () => {
    anchors(ALL)
    renderWithProviders(<PageTour id="planning" />)
    await screen.findByRole('dialog')
    expect(scrollIntoView).toHaveBeenCalledWith(expect.objectContaining({ behavior: 'smooth' }))
  })

  it('reducedMotionScrollsAtOnce', async () => {
    setReducedMotion(true)
    anchors(ALL)
    renderWithProviders(<PageTour id="planning" />)
    await screen.findByRole('dialog')
    expect(scrollIntoView).toHaveBeenCalledWith(expect.objectContaining({ behavior: 'auto' }))
  })
})

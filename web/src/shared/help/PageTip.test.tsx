import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { renderWithProviders } from '@/shared/testing'
import { PageTip } from './PageTip'

beforeEach(() => {
  localStorage.removeItem('liberdade.tips')
})

afterEach(cleanup)

const TEXT = 'Todos os lançamentos do mês. Toque em um para editar ou excluir.'

describe('PageTip', () => {
  it('showsOnTheFirstVisit', async () => {
    renderWithProviders(<PageTip id="transactions" />)

    expect(await screen.findByRole('note')).toHaveTextContent(TEXT)
    expect(screen.getByRole('button', { name: 'Entendi' })).toBeInTheDocument()
  })

  it('gotItHidesItAndItStaysHidden', async () => {
    const first = renderWithProviders(<PageTip id="transactions" />)
    fireEvent.click(await screen.findByRole('button', { name: 'Entendi' }))
    expect(screen.queryByRole('note')).not.toBeInTheDocument()
    first.unmount()

    renderWithProviders(<PageTip id="transactions" />)
    await waitFor(() => expect(localStorage.length).toBeGreaterThan(0))
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByRole('note')).not.toBeInTheDocument()
  })

  it('dismissHidesAcrossScreens', async () => {
    const first = renderWithProviders(<PageTip id="transactions" />)
    fireEvent.click(await screen.findByRole('button', { name: 'Entendi' }))
    first.unmount()

    renderWithProviders(<PageTip id="transactions" />)
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByRole('note')).not.toBeInTheDocument()
  })

  it('hiddenWhenTipsAreOff', async () => {
    localStorage.setItem('liberdade.tips', 'off')
    renderWithProviders(<PageTip id="transactions" />)

    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(screen.queryByRole('note')).not.toBeInTheDocument()
  })
})

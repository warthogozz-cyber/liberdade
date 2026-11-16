import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import type { PersistedClient } from '@tanstack/react-query-persist-client'
import { http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { WelcomePage } from '@/features/onboarding/WelcomePage'
import { mockState, seedMockFamily } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL, type MeView } from '@/shared/api'
import { db } from '@/shared/offline'
import { renderWithProviders } from '@/shared/testing'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function renderWelcome() {
  return renderWithProviders(<WelcomePage />, { path: '/boas-vindas' })
}

function recordPosts() {
  const bodies: unknown[] = []
  server.use(
    http.post(`${API_URL}/v1/workspaces`, async ({ request }) => {
      bodies.push(await request.clone().json())
    }),
  )
  return bodies
}

const startButton = () => screen.getByRole('button', { name: 'Começar' })

/** The family name in the `me` that the device copy of the cache holds, if any. */
async function savedMe(): Promise<string | undefined> {
  const saved = await db.kv.get('liberdade-query-cache')
  if (!saved) return undefined
  const { clientState } = JSON.parse(saved.value) as PersistedClient
  const me = clientState.queries.find((query) => query.queryHash === '["me"]')?.state.data as
    MeView | undefined
  return me?.workspaces[0]?.name
}

describe('WelcomePage', () => {
  it('greets by first name and suggests the family name', async () => {
    renderWelcome()

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Olá, Ana! 👋' }),
    ).toBeInTheDocument()
    const name = screen.getByRole('textbox', { name: 'Nome da família' })
    expect(name).toHaveValue('Família Teste')
    expect(name).toHaveAttribute('maxLength', '60')
    expect(screen.getByText('Dá para mudar depois.')).toBeInTheDocument()
    expect(screen.getByText('Ao começar, criamos:')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Conta "Carteira"',
      'Categorias do dia a dia',
    ])
    expect(document.title).toBe('Boas-vindas · Liberdade')
  })

  it('creates the family with the trimmed name, then opens the new expense', async () => {
    const bodies = recordPosts()
    const { router } = renderWelcome()
    const name = await screen.findByRole('textbox', { name: 'Nome da família' })

    fireEvent.change(name, { target: { value: '  Família Teste  ' } })
    fireEvent.click(startButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/lancar'))
    expect(bodies).toEqual([expect.objectContaining({ name: 'Família Teste' })])
    expect(mockState.workspaces).toHaveLength(1)
  })

  it('saves the new family to the device before opening the new expense (I1)', async () => {
    const { router } = renderWelcome()
    await screen.findByRole('textbox', { name: 'Nome da família' })
    let savedAtNavigation: Promise<string | undefined> | undefined
    router.subscribe(({ location }) => {
      if (location.pathname === '/lancar') savedAtNavigation ??= savedMe()
    })

    fireEvent.click(startButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/lancar'))
    expect(await savedAtNavigation).toBe('Família Teste')
  })

  it('sends one creation for two quick taps', async () => {
    const bodies = recordPosts()
    const { router } = renderWelcome()
    await screen.findByRole('textbox', { name: 'Nome da família' })

    fireEvent.click(startButton())
    fireEvent.click(startButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/lancar'))
    expect(bodies).toHaveLength(1)
  })

  it('disables Começar while the name is blank', async () => {
    renderWelcome()
    const name = await screen.findByRole('textbox', { name: 'Nome da família' })

    fireEvent.change(name, { target: { value: '   ' } })

    expect(startButton()).toBeDisabled()
  })

  it('opens the new expense when the family already exists (409)', async () => {
    server.use(
      http.post(`${API_URL}/v1/workspaces`, () =>
        problem(409, 'workspace.already-exists', 'Você já faz parte de uma família.'),
      ),
    )
    const { router } = renderWelcome()
    await screen.findByRole('textbox', { name: 'Nome da família' })

    fireEvent.click(startButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/lancar'))
  })

  it('shows the error and lets the person try again when the creation fails', async () => {
    server.use(http.post(`${API_URL}/v1/workspaces`, () => problem(500, 'server.error')))
    renderWelcome()
    await screen.findByRole('textbox', { name: 'Nome da família' })

    fireEvent.click(startButton())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não deu para criar a família. Tente de novo.',
    )
    expect(startButton()).toBeEnabled()
  })

  it('disables Começar offline', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    renderWelcome()
    await screen.findByRole('textbox', { name: 'Nome da família' })

    expect(startButton()).toBeDisabled()
    expect(screen.getByText('Criar a família precisa de internet.')).toBeInTheDocument()
  })

  it('goes to the home page when the person already has a family', async () => {
    seedMockFamily()
    const { router } = renderWelcome()

    await waitFor(() => expect(router.state.location.pathname).toBe('/inicio'))
  })
})

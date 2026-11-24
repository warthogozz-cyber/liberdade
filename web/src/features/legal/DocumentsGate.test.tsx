import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { useRoutes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { mockState, seedMockFamily, seedPendingDocuments } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function App() {
  return useRoutes(routes)
}

async function openGate() {
  seedMockFamily()
  seedPendingDocuments()
  renderWithProviders(<App />, { path: '/*', route: '/inicio' })
  await screen.findByRole('heading', { level: 1, name: 'Antes de começar' })
}

const continueButton = () => screen.getByRole('button', { name: 'Continuar' })

describe('DocumentsGate', () => {
  it('continueNeedsCheckbox', async () => {
    await openGate()
    expect(continueButton()).toBeDisabled()
    for (const name of [/Termos de uso/, /Política de privacidade/]) {
      const link = screen.getByRole('link', { name })
      expect(link).toHaveAttribute('target', '_blank')
      expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    }

    fireEvent.click(
      screen.getByRole('checkbox', {
        name: 'Li e aceito os termos de uso e a política de privacidade',
      }),
    )
    expect(continueButton()).toBeEnabled()
    fireEvent.click(continueButton())

    expect(
      await screen.findByRole('navigation', { name: 'Navegação principal' }),
    ).toBeInTheDocument()
    expect(screen.queryByText('Antes de começar')).not.toBeInTheDocument()
  })

  it('reloadsOn409', async () => {
    let lists = 0
    server.use(
      http.get(`${API_URL}/v1/me/documents`, () => {
        lists += 1
        // No answer: the default handler takes the request next.
      }),
      http.post(`${API_URL}/v1/me/documents/accept`, () =>
        problem(409, 'documents.version-outdated', 'Os termos mudaram.'),
      ),
    )
    await openGate()
    expect(lists).toBe(1)

    fireEvent.click(screen.getByRole('checkbox'))
    fireEvent.click(continueButton())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Publicamos uma nova versão dos documentos',
    )
    await waitFor(() => expect(lists).toBe(2))
    expect(screen.getByRole('heading', { level: 1, name: 'Antes de começar' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Recarregar' })).toBeVisible()
  })

  it('postsTheApiCurrentVersion', async () => {
    const sent: unknown[] = []
    server.use(
      http.post(`${API_URL}/v1/me/documents/accept`, async ({ request }) => {
        sent.push(await request.clone().json())
        // No answer: the default handler takes the request next.
      }),
    )
    await openGate()

    fireEvent.click(screen.getByRole('checkbox'))
    fireEvent.click(continueButton())

    expect(
      await screen.findByRole('navigation', { name: 'Navegação principal' }),
    ).toBeInTheDocument()
    expect(sent).toEqual([{ terms: 'terms-2026-12', privacy: 'privacy-2026-12.3' }])
  })

  it('postsTheVersionsTheApiListsEvenWhenOlder', async () => {
    // With billing off the API still asks for the texts from before billing.
    mockState.currentDocuments = { TERMS: 'terms-2026-11', PRIVACY: 'privacy-2026-11.1' }
    const sent: unknown[] = []
    server.use(
      http.post(`${API_URL}/v1/me/documents/accept`, async ({ request }) => {
        sent.push(await request.clone().json())
      }),
    )
    await openGate()

    fireEvent.click(screen.getByRole('checkbox'))
    fireEvent.click(continueButton())

    expect(
      await screen.findByRole('navigation', { name: 'Navegação principal' }),
    ).toBeInTheDocument()
    expect(sent).toEqual([{ terms: 'terms-2026-11', privacy: 'privacy-2026-11.1' }])
  })

  it('missingVersionNeverPosts', async () => {
    // A list without the policy: nothing is posted, rather than an empty version.
    let posts = 0
    server.use(
      http.get(`${API_URL}/v1/me/documents`, () =>
        HttpResponse.json([
          {
            purpose: 'TERMS',
            currentVersion: 'terms-2026-12',
            acceptedVersion: null,
            acceptedAt: null,
            acceptanceRequired: true,
          },
        ]),
      ),
      http.post(`${API_URL}/v1/me/documents/accept`, () => {
        posts += 1
      }),
    )
    await openGate()

    fireEvent.click(screen.getByRole('checkbox'))
    fireEvent.click(continueButton())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não deu para registrar o aceite. Tente de novo.',
    )
    expect(posts).toBe(0)
    expect(screen.getByRole('heading', { level: 1, name: 'Antes de começar' })).toBeVisible()
  })

  it('offlineNeverBlocks', async () => {
    seedMockFamily()
    seedPendingDocuments()
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    server.use(http.get(`${API_URL}/v1/me/documents`, () => HttpResponse.error()))
    renderWithProviders(<App />, { path: '/*', route: '/inicio' })

    expect(
      await screen.findByRole('navigation', { name: 'Navegação principal' }),
    ).toBeInTheDocument()
    expect(screen.queryByText('Antes de começar')).not.toBeInTheDocument()
  })

  it('keptAcceptanceOpensTheAppWithANotice', async () => {
    seedMockFamily()
    // The person accepted the policy before the .1 text: the API says it still counts.
    mockState.acceptedDocuments.PRIVACY = { version: 'privacy-2026-12', at: '2026-11-20T10:00:00Z' }
    renderWithProviders(<App />, { path: '/*', route: '/inicio' })

    expect(
      await screen.findByRole('navigation', { name: 'Navegação principal' }),
    ).toBeInTheDocument()
    expect(screen.queryByText('Antes de começar')).not.toBeInTheDocument()
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Atualizamos a política de privacidade.',
    )
  })

  it('disabledOffline', async () => {
    await openGate()
    fireEvent.click(screen.getByRole('checkbox'))
    expect(continueButton()).toBeEnabled()

    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    window.dispatchEvent(new Event('offline'))

    await waitFor(() => expect(continueButton()).toBeDisabled())
    expect(screen.getByText('Sem conexão. Para aceitar, volte a ficar online.')).toBeVisible()
  })
})

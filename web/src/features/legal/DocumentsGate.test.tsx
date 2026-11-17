import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { useRoutes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { seedMockFamily, seedPendingDocuments } from '@/mocks/data'
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

import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SignOutButton } from '@/features/auth/SignOutButton'
import { mockState } from '@/mocks/data'
import { problem } from '@/mocks/handlers'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { countOutboxForUser } from '@/shared/offline'
import { db } from '@/shared/offline/db'
import { outboxItem, renderWithProviders } from '@/shared/testing'

// Unmounting also closes a dialog left open.
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

function recordLogouts() {
  const calls: string[] = []
  server.use(
    http.post(`${API_URL}/logout`, ({ request }) => {
      calls.push(request.method)
    }),
  )
  return calls
}

async function queue(count: number) {
  for (let index = 0; index < count; index++) {
    await db.outbox.add(
      outboxItem(`t${index}`, { userId: mockState.user.id, createdAt: 1000 + index }),
    )
  }
}

function renderButton() {
  return renderWithProviders(<SignOutButton />, { path: '/mais' })
}

async function signOutButton() {
  const button = await screen.findByRole('button', { name: 'Sair' })
  await waitFor(() => expect(button).toBeEnabled())
  return button
}

describe('SignOutButton', () => {
  it('signs out at once with nothing queued and leaves an empty cache', async () => {
    const logouts = recordLogouts()
    const { router, queryClient } = renderButton()

    fireEvent.click(await signOutButton())

    await waitFor(() => expect(router.state.location.pathname).toBe('/entrar'))
    expect(logouts).toEqual(['POST'])
    await waitFor(() => expect(queryClient.getQueryData(['me'])).toBeUndefined())
  })

  it('tells the other tabs after the sign-out, and not when it fails', async () => {
    const sent: unknown[] = []
    vi.stubGlobal(
      'BroadcastChannel',
      class {
        postMessage(data: unknown) {
          sent.push(data)
        }
        close() {}
      },
    )
    const { router } = renderButton()
    fireEvent.click(await signOutButton())
    await waitFor(() => expect(router.state.location.pathname).toBe('/entrar'))
    await waitFor(() => expect(sent).toEqual([{ type: 'signed-out' }]))
    cleanup()
    sent.length = 0

    server.use(http.post(`${API_URL}/logout`, () => problem(500, 'server.error')))
    renderButton()
    fireEvent.click(await signOutButton())
    await screen.findByRole('alert')
    expect(sent).toEqual([])
  })

  it('warns before signing out with queued expenses', async () => {
    await queue(2)
    renderButton()

    fireEvent.click(await signOutButton())

    const dialog = await screen.findByRole('alertdialog', { name: 'Sair deste aparelho?' })
    expect(dialog).toHaveTextContent(
      'Há 2 gastos aguardando sinal. Sair apaga esses gastos deste aparelho.',
    )
  })

  it('keeps the session and the queue when the person cancels', async () => {
    await queue(1)
    const logouts = recordLogouts()
    renderButton()

    fireEvent.click(await signOutButton())
    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar' }))

    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(logouts).toEqual([])
    expect(await countOutboxForUser(mockState.user.id)).toBe(1)
  })

  it('signs out and empties the queue once the person confirms', async () => {
    await queue(1)
    const logouts = recordLogouts()
    const { router } = renderButton()

    fireEvent.click(await signOutButton())
    fireEvent.click(await screen.findByRole('button', { name: 'Sair mesmo assim' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/entrar'))
    expect(logouts).toEqual(['POST'])
    expect(await countOutboxForUser(mockState.user.id)).toBe(0)
  })

  it('keeps the queue and says so when the sign-out fails', async () => {
    await queue(1)
    server.use(http.post(`${API_URL}/logout`, () => problem(500, 'server.error')))
    const { router } = renderButton()

    fireEvent.click(await signOutButton())
    fireEvent.click(await screen.findByRole('button', { name: 'Sair mesmo assim' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Não deu para sair. Tente de novo.')
    expect(router.state.location.pathname).toBe('/mais')
    expect(await countOutboxForUser(mockState.user.id)).toBe(1)
  })

  it('says so and does nothing else when the queue cannot be counted', async () => {
    const logouts = recordLogouts()
    vi.spyOn(db.outbox, 'where').mockImplementation(() => {
      throw new Error('IndexedDB is gone')
    })
    const { router } = renderButton()

    fireEvent.click(await signOutButton())

    expect(await screen.findByRole('alert')).toHaveTextContent('Não deu para sair. Tente de novo.')
    expect(screen.getByRole('button', { name: 'Sair' })).toBeEnabled()
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(logouts).toEqual([])
    expect(router.state.location.pathname).toBe('/mais')
  })

  it('leaves the screens before it forgets the cache', async () => {
    const { router, queryClient } = renderButton()
    const clear = queryClient.clear.bind(queryClient)
    const pathsAtClear: string[] = []
    vi.spyOn(queryClient, 'clear').mockImplementation(() => {
      pathsAtClear.push(router.state.location.pathname)
      clear()
    })

    fireEvent.click(await signOutButton())

    await waitFor(() => expect(pathsAtClear).toEqual(['/entrar']))
  })

  it('is disabled offline', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    renderButton()

    expect(await screen.findByRole('button', { name: 'Sair' })).toBeDisabled()
    expect(screen.getByText('Sair precisa de internet.')).toBeInTheDocument()
  })
})

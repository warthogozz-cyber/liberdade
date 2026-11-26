import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { InstagramEmbed } from './InstagramEmbed'

const URL_OF_THE_PROFILE = 'https://www.instagram.com/exemplo.loja/'
const HTML =
  '<blockquote class="instagram-media" data-instgrm-permalink="https://www.instagram.com/p/x/"></blockquote>'

afterEach(() => {
  cleanup()
  localStorage.clear()
})

function setup(answer: () => Response = () => HttpResponse.json({ html: HTML })) {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { markets: true }
  const calls: string[] = []
  server.use(
    http.post(`${API_URL}/v1/workspaces/:ws/markets/:ref/instagram-embed`, () => {
      calls.push('embed')
      return answer()
    }),
  )
  renderWithProviders(
    <InstagramEmbed
      userId="u-instagram-test"
      workspaceId={workspace.id}
      marketRef="osm-n-9001"
      url={URL_OF_THE_PROFILE}
    />,
  )
  return calls
}

async function accept() {
  fireEvent.click(screen.getByRole('button', { name: 'Ver aqui' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Ver publicação' }))
}

describe('InstagramEmbed', () => {
  it('noRequestBeforeNotice', async () => {
    const calls = setup()
    fireEvent.click(screen.getByRole('button', { name: 'Ver aqui' }))
    expect(await screen.findByText(/A publicação é do Instagram, da Meta/)).toBeVisible()
    expect(calls).toEqual([])

    fireEvent.click(screen.getByRole('button', { name: 'Agora não' }))
    await new Promise((resolve) => setTimeout(resolve, 30))
    expect(calls).toEqual([])
    expect(document.querySelector('iframe')).toBeNull()
  })

  it('sandboxWithoutSameOrigin', async () => {
    setup()
    await accept()

    const frame = (await waitFor(() => {
      const found = document.querySelector('iframe')
      expect(found).not.toBeNull()
      return found
    })) as HTMLIFrameElement
    expect(frame.getAttribute('sandbox')).toBe(
      'allow-scripts allow-popups allow-popups-to-escape-sandbox',
    )
    expect(frame.getAttribute('src')).toBe('/embed/instagram')
    expect(frame.getAttribute('title')).toBe('Publicação do Instagram')
    expect(frame.className).toContain('min-h-[560px]')
  })

  it('postsTheHtmlOnLoad', async () => {
    setup()
    await accept()
    const frame = await waitFor(() => {
      const found = document.querySelector('iframe')
      expect(found).not.toBeNull()
      return found as HTMLIFrameElement
    })
    const post = vi.spyOn(frame.contentWindow as Window, 'postMessage')

    fireEvent.load(frame)

    expect(post).toHaveBeenCalledWith({ type: 'liberdade-instagram', html: HTML }, '*')
  })

  it('unavailableKeepsTheLink', async () => {
    setup(() =>
      HttpResponse.json(
        { type: 'about:blank', title: 'x', status: 422, code: 'markets.instagram_unavailable' },
        { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )
    await accept()

    expect(await screen.findByText('Este perfil não pode ser mostrado aqui')).toBeVisible()
    const link = screen.getByRole('link', { name: 'Abrir no Instagram' })
    expect(link).toHaveAttribute('href', URL_OF_THE_PROFILE)
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    expect(document.querySelector('iframe')).toBeNull()
  })
})

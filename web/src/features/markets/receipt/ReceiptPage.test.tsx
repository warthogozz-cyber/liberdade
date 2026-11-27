import { cleanup, fireEvent, screen } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { writeLocation } from '../locationStore'
import { ReceiptPage } from './ReceiptPage'
import { makeChave } from './testing/nfceFixtures'

const chave = makeChave({ aamm: '2611' })
const grouped = chave.replace(/(.{4})/g, '$1 ').trim()
const BUYER = '12345678909'
const LOOKUP = `${API_URL}/v1/workspaces/:ws/markets/receipts/lookup`
const SAVE = `${API_URL}/v1/workspaces/:ws/markets/receipts`
const SEARCH = `${API_URL}/v1/workspaces/:ws/markets/search`
const KNOWN = {
  ref: 'osm-n-9001',
  familyId: '0192f3a4-0000-7000-8000-0000000000c1',
  name: 'Mercado Exemplo 1',
  kind: 'SUPERMARKET',
  status: 'ACTIVE',
}
const problemHeaders = { 'Content-Type': 'application/problem+json' }

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date(2026, 10, 26, 12))
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  sessionStorage.clear()
  vi.useRealTimers()
})

function seed(role: 'OWNER' | 'ADULT' | 'YOUTH' | 'VIEWER' = 'OWNER') {
  const workspace = seedMockFamily()
  workspace.role = role
  mockState.modules[workspace.id] = { markets: true }
  writeLocation(mockState.user.id, {
    mode: 'approximate',
    point: { lat: '-10.00', lon: '-50.00' },
  })
  return workspace
}

function render() {
  return renderWithProviders(null, {
    routes: [
      { path: '/mais/feira/nota', element: <ReceiptPage /> },
      { path: '/mais/feira', element: <p>Lista da Feira</p> },
    ],
    route: '/mais/feira/nota',
  })
}

function answerLookup(market: typeof KNOWN | null, captured?: unknown[]) {
  server.use(
    http.post(LOOKUP, async ({ request }) => {
      captured?.push(await request.json())
      return HttpResponse.json({
        mode: 'MANUAL',
        uf: 'RS',
        issuedMonth: '2026-11',
        market,
        items: [],
      })
    }),
  )
}

const typeKey = async (text = grouped) => {
  fireEvent.click(await screen.findByRole('button', { name: 'Digitar a chave' }))
  fireEvent.change(screen.getByLabelText('Chave de 44 números'), { target: { value: text } })
  fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))
}

const pasteLink = async (text: string) => {
  fireEvent.click(await screen.findByRole('button', { name: 'Colar o link' }))
  fireEvent.change(screen.getByLabelText('Link da nota'), { target: { value: text } })
  fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))
}

describe('ReceiptPage', () => {
  it('helpText', async () => {
    localStorage.setItem('liberdade.tips', 'on')
    seed()
    render()

    expect(await screen.findByRole('note')).toHaveTextContent(
      'Aponte a câmera para o QR da nota. Os preços ficam guardados; a nota não vira gasto.',
    )
  })

  it('lookupBodyHasNoBuyerDocument', async () => {
    seed()
    const bodies: unknown[] = []
    answerLookup(KNOWN, bodies)
    render()

    const offline = `${chave}|3|1|09|10.00|1|${BUYER}|c2lnbmF0dXJl`
    await pasteLink(`https://sefaz.example.test/qr?p=${encodeURIComponent(offline)}`)

    await screen.findByText('Esta nota é deste mercado')
    expect(bodies).toEqual([{ chave, version: 3, p: `${chave}|3|1` }])
    expect(JSON.stringify(bodies)).not.toContain(BUYER)
  })

  it('duplicateMessage', async () => {
    seed()
    server.use(
      http.post(LOOKUP, () =>
        HttpResponse.json(
          { title: 'Conflito', status: 409, code: 'receipt.duplicate' },
          { status: 409, headers: problemHeaders },
        ),
      ),
    )
    render()

    await typeKey()

    expect(await screen.findByText('Esta nota já foi registrada.')).toBeVisible()
  })

  it('invalidKeyFromTheApi', async () => {
    seed()
    server.use(
      http.post(LOOKUP, () =>
        HttpResponse.json(
          { title: 'Chave', status: 422, code: 'receipt.key_invalid' },
          { status: 422, headers: problemHeaders },
        ),
      ),
    )
    render()

    await typeKey()

    expect(
      await screen.findByText('Esta chave não parece de uma nota fiscal de consumidor válida.'),
    ).toBeVisible()
  })

  it('showsTheCheckDigitErrorWithoutCallingTheApi', async () => {
    seed()
    const bodies: unknown[] = []
    answerLookup(null, bodies)
    render()

    await typeKey(grouped.slice(0, -1) + (chave.endsWith('0') ? '1' : '0'))

    expect(
      await screen.findByText('A chave não confere. Veja se algum número foi digitado errado.'),
    ).toBeVisible()
    expect(bodies).toEqual([])
  })

  it('knownMarketCannotChange', async () => {
    seed()
    answerLookup(KNOWN)
    render()

    await typeKey()

    expect(await screen.findByText('Esta nota é deste mercado')).toBeVisible()
    expect(screen.getByText('Mercado Exemplo 1')).toBeVisible()
    expect(screen.queryByText('Mercado Exemplo 3')).not.toBeInTheDocument()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
  })

  it.each([
    ['ARCHIVED', 'Arquivado. A nota é registrada neste mercado mesmo assim.'],
    ['LEFT_OSM', 'Saiu do mapa. A nota é registrada neste mercado mesmo assim.'],
  ])('knownMarketWithStatus%sShowsTheNoticeAndContinues', async (status, notice) => {
    seed()
    answerLookup({ ...KNOWN, status })
    render()

    await typeKey()

    expect(await screen.findByText(notice)).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))
    expect(await screen.findByLabelText('Data da compra')).toBeVisible()
  })

  it('activeKnownMarketShowsNoStatusNotice', async () => {
    seed()
    answerLookup(KNOWN)
    render()

    await typeKey()

    await screen.findByText('Esta nota é deste mercado')
    expect(screen.queryByText(/mesmo assim/)).not.toBeInTheDocument()
  })

  it('unknownMarketOffersNearbyAndNew', async () => {
    seed()
    answerLookup(null)
    render()

    await typeKey()

    expect(await screen.findByRole('radio', { name: /Mercado Exemplo 3/ })).toBeVisible()
    expect(screen.getByRole('radio', { name: 'Outro mercado' })).toBeVisible()
  })

  it('focusMovesToEachNewStep', async () => {
    seed()
    answerLookup(KNOWN)
    render()

    const keyHeading = await screen.findByRole('heading', { name: 'A nota' })
    expect(keyHeading).not.toHaveFocus()
    await typeKey()

    expect(await screen.findByRole('heading', { name: 'O mercado' })).toHaveFocus()
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))
    expect(await screen.findByRole('heading', { name: 'Os preços' })).toHaveFocus()
  })

  it('marketSearchShowsLoading', async () => {
    seed()
    answerLookup(null)
    server.use(http.post(SEARCH, () => delay('infinite')))
    render()

    await typeKey()

    expect(await screen.findByRole('status')).toHaveTextContent('Carregando os mercados…')
    expect(screen.getByRole('radio', { name: 'Outro mercado' })).toBeVisible()
  })

  it('marketSearchWithNothingNearbySaysSo', async () => {
    seed()
    answerLookup(null)
    server.use(http.post(SEARCH, () => HttpResponse.json({ osm: [], family: [] })))
    render()

    await typeKey()

    expect(
      await screen.findByText('Nenhum mercado conhecido por perto. Escolha Outro mercado.'),
    ).toBeVisible()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('marketSearchFailureOffersRetry', async () => {
    seed()
    answerLookup(null)
    server.use(
      http.post(
        SEARCH,
        () =>
          HttpResponse.json(
            { title: 'Erro', status: 500, code: 'internal', detail: 'Falhou' },
            { status: 500, headers: problemHeaders },
          ),
        { once: true },
      ),
    )
    render()

    await typeKey()

    expect(
      await screen.findByText('Não deu para carregar os mercados. Tente de novo.'),
    ).toBeVisible()
    expect(screen.getByRole('radio', { name: 'Outro mercado' })).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }))
    expect(await screen.findByRole('radio', { name: /Mercado Exemplo 3/ })).toBeVisible()
  })

  it('sefazLinkNeverSent', async () => {
    seed()
    const posted: string[] = []
    server.events.on('request:start', ({ request }) => {
      if (request.method === 'POST')
        void request
          .clone()
          .text()
          .then((text) => posted.push(text))
    })
    answerLookup(KNOWN)
    const link = `https://sefaz.example.test/qr?p=${chave}|3|1`
    render()
    await pasteLink(link)
    await screen.findByText('Esta nota é deste mercado')
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))

    const anchor = await screen.findByRole('link', {
      name: 'Abrir a nota no site da Fazenda (SEFAZ)',
    })
    expect(anchor).toHaveAttribute('href', link)
    expect(anchor).toHaveAttribute('rel', 'noopener noreferrer')
    expect(anchor).toHaveAttribute('target', '_blank')
    expect(posted.length).toBeGreaterThan(0)
    expect(posted.join('\n')).not.toContain('sefaz.example.test')
    server.events.removeAllListeners()
  })

  it('typedKeyHasNoSefazLink', async () => {
    seed()
    answerLookup(KNOWN)
    render()
    await typeKey()
    await screen.findByText('Esta nota é deste mercado')
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))

    expect(await screen.findByText(/Digite os itens à mão\./)).toBeVisible()
    expect(screen.queryByRole('link', { name: /SEFAZ/ })).not.toBeInTheDocument()
  })

  it('endsWithTheCopy', async () => {
    seed()
    answerLookup(KNOWN)
    const saved: unknown[] = []
    server.use(
      http.post(SAVE, async ({ request }) => {
        saved.push(await request.json())
        return HttpResponse.json(
          { receiptId: crypto.randomUUID(), marketRef: KNOWN.ref, prices: 1 },
          { status: 201 },
        )
      }),
    )
    render()
    await typeKey()
    await screen.findByText('Esta nota é deste mercado')
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))

    await screen.findByLabelText('Data da compra')
    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'leite' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Leite integral' }))
    fireEvent.change(screen.getByLabelText('Preço da unidade'), { target: { value: '4,99' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gravar' }))

    expect(
      await screen.findByText(
        'Preços registrados. A nota não vira um gasto; para lançar, use Lançar.',
      ),
    ).toBeVisible()
    expect(saved).toMatchObject([
      {
        chave,
        version: 0,
        issuedOn: '2026-11-26',
        market: { ref: KNOWN.ref },
        newMarket: null,
        items: [{ unit: 'L', unitPrice: '4.99' }],
      },
    ])
    expect(screen.getByRole('link', { name: 'Ver o cartão do mercado' })).toHaveAttribute(
      'href',
      `/mais/feira/mercado/${KNOWN.ref}`,
    )
  })

  async function reachItems(code: string, status: number, detail: string) {
    seed()
    answerLookup(null)
    server.use(
      http.post(SAVE, () =>
        HttpResponse.json(
          { title: 'Conflito', status, code, detail },
          { status, headers: problemHeaders },
        ),
      ),
    )
    render()
    await typeKey()
    fireEvent.click(await screen.findByRole('radio', { name: 'Outro mercado' }))
    fireEvent.change(screen.getByLabelText('Nome do mercado'), { target: { value: 'Padaria' } })
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))
    await screen.findByLabelText('Data da compra')
    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'pão' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Novo: pão' }))
    fireEvent.change(screen.getByLabelText('Preço da unidade'), { target: { value: '1,50' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gravar' }))
  }

  it('cnpjDiffersShowsApiDetailAndChangeMarket', async () => {
    const detail = 'Este mercado já tem outro CNPJ. Confira se é o mercado da nota.'
    await reachItems('receipt.market_cnpj_differs', 409, detail)

    expect(await screen.findByText(detail)).toBeVisible()
    expect(screen.queryByText('Esta nota já foi registrada.')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Trocar o mercado' }))

    fireEvent.click(await screen.findByRole('radio', { name: /Mercado Exemplo 3/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))
    await screen.findByLabelText('Data da compra')
    expect(screen.getByText('pão')).toBeVisible()
    expect(screen.getByLabelText('Preço da unidade')).toHaveValue('1,50')
    expect(screen.queryByText(detail)).not.toBeInTheDocument()
  })

  it('productSearchTextSurvivesChangingTheMarket', async () => {
    await reachItems('receipt.market_cnpj_differs', 409, 'Detalhe qualquer')
    await screen.findByText('Detalhe qualquer')
    fireEvent.click(screen.getByRole('button', { name: 'Adicionar item' }))
    const fields = screen.getAllByLabelText('Produto')
    fireEvent.change(fields[fields.length - 1]!, { target: { value: 'arr' } })

    fireEvent.click(screen.getByRole('button', { name: 'Trocar o mercado' }))
    fireEvent.click(await screen.findByRole('radio', { name: /Mercado Exemplo 3/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))
    await screen.findByLabelText('Data da compra')

    expect(screen.getByLabelText('Produto')).toHaveValue('arr')
  })

  it('cnpjTakenOffersChangeMarket', async () => {
    const detail = 'Outro mercado já usa este CNPJ.'
    await reachItems('market.cnpj_taken', 409, detail)

    expect(await screen.findByText(detail)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Trocar o mercado' })).toBeVisible()
  })

  it('duplicateOnSaveKeepsTheDuplicateCopy', async () => {
    await reachItems('receipt.duplicate', 409, 'Detalhe qualquer')

    expect(await screen.findByText('Esta nota já foi registrada.')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Trocar o mercado' })).not.toBeInTheDocument()
  })

  it('nothingInLocalStorage', async () => {
    seed()
    answerLookup(null)
    render()
    await typeKey()
    fireEvent.click(await screen.findByRole('radio', { name: 'Outro mercado' }))
    fireEvent.change(screen.getByLabelText('Nome do mercado'), { target: { value: 'Padaria' } })
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))
    await screen.findByLabelText('Data da compra')
    fireEvent.change(screen.getByLabelText('Produto'), { target: { value: 'pão' } })
    fireEvent.click(await screen.findByRole('button', { name: 'Novo: pão' }))
    fireEvent.change(screen.getByLabelText('Preço da unidade'), { target: { value: '1,50' } })
    fireEvent.click(screen.getByRole('button', { name: 'Gravar' }))
    await screen.findByText(/Preços registrados/)

    for (let index = 0; index < localStorage.length; index += 1) {
      const key = localStorage.key(index) as string
      expect(key).not.toContain(chave)
      expect(localStorage.getItem(key)).not.toContain(chave)
    }
    expect(sessionStorage.length).toBe(0)
  })

  it.each(['YOUTH', 'VIEWER'] as const)('hiddenFor%s', async (role) => {
    seed(role)
    const { router } = render()

    expect(await screen.findByText('Lista da Feira')).toBeVisible()
    expect(router.state.location.pathname).toBe('/mais/feira')
    expect(screen.queryByRole('button', { name: 'Digitar a chave' })).not.toBeInTheDocument()
  })
})

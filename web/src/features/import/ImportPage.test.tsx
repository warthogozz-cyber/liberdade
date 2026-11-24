import { act, cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedFamilyPlan, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { ImportPage } from './ImportPage'
import { fixtureBytes } from './readers/fixtures.test-util'

const PATH = '/lancamentos/importar'

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime('2026-11-15T15:00:00Z')
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

function renderImport() {
  return renderWithProviders(null, {
    route: PATH,
    routes: [
      { path: PATH, element: <ImportPage /> },
      { path: '/lancamentos', element: <p>Lista de lançamentos</p> },
    ],
  })
}

/** An adult of a family on the paid plan: the one who may import. */
function seedAdultWithPlan() {
  const workspace = seedMockFamily()
  workspace.role = 'ADULT'
  seedFamilyPlan()
  return workspace
}

function fileOf(content: Uint8Array | string, name: string): File {
  return new File([content as BlobPart], name)
}

async function fixtureFile(name: string): Promise<File> {
  return fileOf(await fixtureBytes(name), name)
}

/** A plain OFX with these lines, all on November 3rd. */
function ofx(lines: Array<[amount: string, memo: string]>): File {
  const body = lines
    .map(
      ([amount, memo]) =>
        `<STMTTRN>\n<TRNTYPE>OTHER\n<DTPOSTED>20261103\n<TRNAMT>${amount}\n<MEMO>${memo}\n</STMTTRN>`,
    )
    .join('\n')
  return fileOf(
    `OFXHEADER:100\nDATA:OFXSGML\nCHARSET:1252\n\n<OFX>\n<BANKTRANLIST>\n${body}\n</BANKTRANLIST>\n</OFX>\n`,
    'extrato.ofx',
  )
}

async function chooseFile(file: File) {
  await screen.findByRole('option', { name: 'Carteira' })
  fireEvent.change(screen.getByLabelText('Arquivo do extrato'), { target: { files: [file] } })
}

const preview = () => screen.findByRole('list', { name: 'Confira os lançamentos' })

/** The preview line whose checkbox is named like `name`. */
function line(name: RegExp, index = 0) {
  const box = screen.getAllByRole('checkbox', { name })[index]
  return within(box.closest('li')!)
}

const importButton = () => screen.getByRole('button', { name: /^Importar \d+ lançament/ })

describe('ImportPage', () => {
  it('readsAnOfxAndShowsThePreview', async () => {
    seedAdultWithPlan()
    renderImport()

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Importar extrato' }),
    ).toBeInTheDocument()
    fireEvent.change(await screen.findByLabelText('Conta'), {
      target: { value: mockState.accounts[0].id },
    })
    await chooseFile(await fixtureFile('ofx-sgml-1252.ofx'))

    const list = within(await preview())
    expect(list.getAllByRole('checkbox')).toHaveLength(4)
    const padaria = list.getByRole('checkbox', {
      name: /^03\/11\/2026 PADARIA PAO QUENTE −R\$\s45,90$/,
    })
    expect(padaria).toBeChecked()
    expect(
      list.getAllByRole('checkbox', { name: /^04\/11\/2026 MERCADO CENTRAL −R\$\s12,50$/ }),
    ).toHaveLength(2)
    expect(
      list.getByRole('checkbox', { name: /^05\/11\/2026 Doação mensal \+R\$\s100,00$/ }),
    ).toBeChecked()
    // The category the family last used for the description comes suggested.
    expect(line(/PADARIA/).getByRole('radio', { name: '🛒 Mercado' })).toBeChecked()
    expect(line(/MERCADO CENTRAL/).queryByRole('radio', { checked: true })).toBeNull()
    expect(document.title).toBe('Importar extrato · Liberdade')
  })

  it('csvWithoutCertaintyAsksForColumns', async () => {
    seedAdultWithPlan()
    renderImport()
    const csv =
      'Quando;O que;Quanto;Outro\n03/11/2026;LOJA A;-45,90;1,00\n04/11/2026;LOJA B;-12,50;2,00\n'

    await chooseFile(fileOf(csv, 'extrato.csv'))

    expect(
      await screen.findByRole('heading', { level: 2, name: 'Quais colunas são quais?' }),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))

    const list = within(await preview())
    expect(list.getByRole('checkbox', { name: /^03\/11\/2026 LOJA A −R\$\s45,90$/ })).toBeChecked()
    expect(list.getByRole('checkbox', { name: /^04\/11\/2026 LOJA B −R\$\s12,50$/ })).toBeChecked()
    expect(screen.queryByRole('heading', { name: 'Quais colunas são quais?' })).toBeNull()
  })

  it('bulkCategory', async () => {
    seedAdultWithPlan()
    renderImport()

    await chooseFile(
      ofx([
        ['-10.00', 'LOJA A'],
        ['-11.00', 'LOJA B'],
        ['-12.00', 'LOJA C'],
        ['-13.00', 'LOJA D'],
        ['50.00', 'RECEBIDO'],
      ]),
    )
    await preview()
    fireEvent.click(line(/LOJA A/).getByRole('radio', { name: '🛒 Mercado' }))
    fireEvent.click(
      line(/LOJA A/).getByRole('button', { name: 'Usar a mesma categoria nas 3 sem categoria' }),
    )

    for (const name of [/LOJA B/, /LOJA C/, /LOJA D/]) {
      expect(line(name).getByRole('radio', { name: '🛒 Mercado' })).toBeChecked()
    }
    // An income keeps its own choice.
    expect(line(/RECEBIDO/).queryByRole('radio', { checked: true })).toBeNull()
    expect(screen.queryByRole('button', { name: /Usar a mesma categoria/ })).toBeNull()
  })

  it('importNeedsCategoryAndNetwork', async () => {
    seedAdultWithPlan()
    renderImport()

    await chooseFile(await fixtureFile('ofx-sgml-1252.ofx'))
    await preview()

    // The two Mercado lines and the donation are checked without a category.
    expect(importButton()).toBeDisabled()
    expect(screen.getByText('Falta escolher a categoria de 3 linhas marcadas.')).toBeInTheDocument()
    fireEvent.click(line(/MERCADO CENTRAL/, 0).getByRole('checkbox'))
    fireEvent.click(line(/MERCADO CENTRAL/, 1).getByRole('checkbox'))
    fireEvent.click(line(/Doação/).getByRole('checkbox'))
    expect(importButton()).toHaveAccessibleName('Importar 1 lançamento')
    expect(importButton()).toBeEnabled()

    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    act(() => {
      window.dispatchEvent(new Event('offline'))
    })

    expect(importButton()).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent(
      'Sem conexão. A importação precisa de internet.',
    )
  })

  it('showsTheResult', async () => {
    seedAdultWithPlan()
    const sent: unknown[] = []
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/accounts/:accountId/imports`, async ({ request }) => {
        sent.push(await request.json())
        return HttpResponse.json({ created: 3, confirmed: 0, skipped: 1 })
      }),
    )
    renderImport()

    await chooseFile(await fixtureFile('ofx-sgml-1252.ofx'))
    await preview()
    fireEvent.click(line(/MERCADO CENTRAL/, 0).getByRole('radio', { name: '🛒 Mercado' }))
    fireEvent.click(
      line(/MERCADO CENTRAL/, 0).getByRole('button', {
        name: 'Usar a mesma categoria na 1 sem categoria',
      }),
    )
    fireEvent.click(line(/Doação/).getByRole('radio', { name: '🎁 Presentes' }))
    fireEvent.click(importButton())

    expect(
      await screen.findByText('3 entraram, 0 confirmaram um previsto, 1 já existia'),
    ).toBeInTheDocument()
    expect(sent).toHaveLength(1)
    expect(screen.getByRole('link', { name: 'Ver os lançamentos' })).toHaveAttribute(
      'href',
      '/lancamentos',
    )
    expect(screen.queryByRole('list', { name: 'Confira os lançamentos' })).toBeNull()
  })

  it('unreadableFile', async () => {
    seedAdultWithPlan()
    renderImport()
    const png = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d])

    await chooseFile(fileOf(png, 'foto.png'))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não conseguimos ler este arquivo. Ele precisa ser OFX ou CSV exportado pelo banco.',
    )
    expect(screen.queryByRole('list', { name: 'Confira os lançamentos' })).toBeNull()
  })

  it('tooLargeFile', async () => {
    seedAdultWithPlan()
    renderImport()
    const big = fileOf('OFXHEADER:100', 'grande.ofx')
    Object.defineProperty(big, 'size', { value: 6 * 1024 * 1024 })

    await chooseFile(big)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Este arquivo é grande demais. Exporte um período menor.',
    )
  })

  it('redirectsWithoutAccess', async () => {
    const workspace = seedMockFamily()
    workspace.role = 'VIEWER'
    seedFamilyPlan()
    const viewer = renderImport()
    expect(await screen.findByText('Lista de lançamentos')).toBeInTheDocument()
    expect(viewer.router.state.location.pathname).toBe('/lancamentos')
    cleanup()

    workspace.role = 'ADULT'
    mockState.billing.plan = { key: 'FREE', endsAt: null }
    const free = renderImport()
    expect(await screen.findByText('Lista de lançamentos')).toBeInTheDocument()
    await waitFor(() => expect(free.router.state.location.pathname).toBe('/lancamentos'))
  })
})

import { act, cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedFamilyPlan, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { plannedView, renderWithProviders } from '@/shared/testing'
import { loadMapping } from './csvMappingStore'
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

  it('adjustColumnsFromThePreview', async () => {
    seedAdultWithPlan()
    renderImport()
    // 1.234 leans to a comma decimal and 12.5 is surely a dot one: not certain, so the columns come first.
    const csv = 'Data;Descricao;Valor\n03/11/2026;LOJA A;-1.234\n04/11/2026;LOJA B;-12.5\n'

    await chooseFile(fileOf(csv, 'extrato.csv'))
    await screen.findByRole('heading', { level: 2, name: 'Quais colunas são quais?' })
    expect(screen.getByLabelText('Como os centavos aparecem')).toHaveValue('.')
    expect(screen.queryByRole('button', { name: 'Voltar sem mudar' })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))

    let list = within(await preview())
    expect(list.getAllByRole('checkbox')).toHaveLength(1)
    expect(list.getByRole('checkbox', { name: /^04\/11\/2026 LOJA B −R\$\s12,50$/ })).toBeChecked()
    expect(screen.getByText('1 linha ignorada (sem data ou valor)')).toBeInTheDocument()

    // Back to the columns and out again without a change: the same preview.
    fireEvent.click(screen.getByRole('button', { name: 'Ajustar colunas' }))
    expect(
      screen.getByText('Confira qual coluna é qual e como os centavos aparecem.'),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Voltar sem mudar' }))
    list = within(await preview())
    expect(list.getAllByRole('checkbox')).toHaveLength(1)

    // Now with the comma: the file is read again.
    fireEvent.click(screen.getByRole('button', { name: 'Ajustar colunas' }))
    fireEvent.change(screen.getByLabelText('Como os centavos aparecem'), {
      target: { value: ',' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Continuar' }))
    list = within(await preview())
    expect(
      list.getByRole('checkbox', { name: /^03\/11\/2026 LOJA A −R\$\s1\.234,00$/ }),
    ).toBeChecked()
    expect(screen.getByText('1 linha ignorada (sem data ou valor)')).toBeInTheDocument()

    // The adjusted mapping is remembered for the account once the import starts.
    fireEvent.click(line(/LOJA A/).getByRole('radio', { name: '🛒 Mercado' }))
    fireEvent.click(importButton())
    await screen.findByRole('heading', { name: 'Importação concluída' })
    expect(loadMapping(mockState.accounts[0].id, 'data|descricao|valor|;')?.decimal).toBe(',')
  })

  it('balanceRowsComeUncheckedWithABadge', async () => {
    seedAdultWithPlan()
    renderImport()

    await chooseFile(await fixtureFile('csv-balance-rows.csv'))
    const list = within(await preview())
    expect(list.getAllByRole('checkbox')).toHaveLength(5)
    for (const name of [/SALDO ANTERIOR/, /SALDO DO DIA/, /S A L D O/]) {
      expect(line(name).getByRole('checkbox')).not.toBeChecked()
      expect(line(name).getByText('Saldo?')).toBeInTheDocument()
      expect(
        line(name).getByText(
          'Parece uma linha de saldo, não um lançamento, por isso ficou desmarcada.',
        ),
      ).toBeInTheDocument()
    }
    expect(line(/PADARIA/).getByRole('checkbox')).toBeChecked()
    expect(line(/PADARIA/).queryByText('Saldo?')).toBeNull()
    expect(screen.getByRole('button', { name: 'Ajustar colunas' })).toBeInTheDocument()
    expect(screen.queryByText(/ignorada/)).toBeNull()
  })

  it('hintsOutsideTheCategoryListAreDropped', async () => {
    seedAdultWithPlan()
    const income = mockState.categories.find((c) => c.kind === 'INCOME')!
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/imports/category-hints`, async ({ request }) => {
        const body = (await request.json()) as { items: Array<{ kind: string; key: string }> }
        return HttpResponse.json({
          hints: body.items.map((item) => ({
            categoryId: item.key.startsWith('padaria')
              ? '0192f5a0-0000-7000-8000-0000000000ff'
              : item.key.startsWith('mercado')
                ? income.id
                : null,
          })),
        })
      }),
    )
    renderImport()

    await chooseFile(await fixtureFile('ofx-sgml-1252.ofx'))
    await preview()
    // An unknown id and an income category for an expense are no suggestion.
    expect(line(/PADARIA/).queryByRole('radio', { checked: true })).toBeNull()
    expect(line(/MERCADO CENTRAL/).queryByRole('radio', { checked: true })).toBeNull()
    expect(screen.getByText('Falta escolher a categoria de 4 linhas marcadas.')).toBeInTheDocument()
  })

  it('aLineConfirmingAPlannedOneKeepsItsCategory', async () => {
    const workspace = seedAdultWithPlan()
    mockState.modules[workspace.id] = { planning: true }
    const mercado = mockState.categories.find((c) => c.name === 'Mercado')!
    mockState.transactions.push(
      plannedView(
        {
          id: '0192f5a0-0000-7000-8000-0000000000a1',
          kind: 'EXPENSE',
          amount: { amount: '45.90', currency: 'BRL' },
          occurredOn: '2026-11-03',
          accountId: mockState.accounts[0].id,
          categoryId: mercado.id,
        },
        '2026-11-03',
      ),
    )
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/imports/category-hints`, async ({ request }) => {
        const body = (await request.json()) as { items: unknown[] }
        return HttpResponse.json({ hints: body.items.map(() => ({ categoryId: null })) })
      }),
    )
    renderImport()

    await chooseFile(await fixtureFile('ofx-sgml-1252.ofx'))
    await preview()
    const padaria = line(/PADARIA/)
    expect(padaria.getByText('Confirmar o previsto')).toBeInTheDocument()
    expect(padaria.getByText('Categoria do previsto: Mercado')).toBeInTheDocument()
    expect(padaria.queryByRole('radio')).toBeNull()
    // Only the other three lines still need a category.
    expect(screen.getByText('Falta escolher a categoria de 3 linhas marcadas.')).toBeInTheDocument()
  })

  it('saysWhyImportIsOffWithNothingChecked', async () => {
    seedAdultWithPlan()
    renderImport()

    await chooseFile(ofx([['-10.00', 'LOJA A']]))
    await preview()
    expect(screen.queryByText('Marque ao menos um lançamento.')).toBeNull()
    fireEvent.click(line(/LOJA A/).getByRole('checkbox'))
    expect(importButton()).toBeDisabled()
    expect(importButton()).toHaveAccessibleName('Importar 0 lançamentos')
    expect(screen.getByText('Marque ao menos um lançamento.')).toBeInTheDocument()
  })

  it('aUtf16FileIsRead', async () => {
    seedAdultWithPlan()
    renderImport()
    const text = 'Data;Descrição;Valor\n03/11/2026;AÇOUGUE;-45,90\n04/11/2026;PADARIA;-12,50\n'
    const units = [...text].map((c) => c.charCodeAt(0))
    const bytes = new Uint8Array([0xff, 0xfe, ...units.flatMap((u) => [u & 0xff, u >> 8])])

    await chooseFile(fileOf(bytes, 'extrato.csv'))
    const list = within(await preview())
    expect(list.getByRole('checkbox', { name: /^03\/11\/2026 AÇOUGUE −R\$\s45,90$/ })).toBeChecked()
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

  it('aRefusedImportShowsTheReasonWithoutRetry', async () => {
    seedAdultWithPlan()
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/accounts/:accountId/imports`, () =>
        HttpResponse.json(
          { status: 422, code: 'import.invalid', detail: 'A conta não aceita importação.' },
          { status: 422 },
        ),
      ),
    )
    renderImport()

    await chooseFile(ofx([['-10.00', 'PADARIA PAO QUENTE']]))
    await preview()
    fireEvent.click(importButton())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'O servidor recusou o envio, então tentar de novo não resolve: A conta não aceita importação.',
    )
    expect(screen.queryByRole('button', { name: 'Tentar de novo' })).toBeNull()
  })

  it('aConflictKeepsTheRetry', async () => {
    seedAdultWithPlan()
    let calls = 0
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/accounts/:accountId/imports`, () => {
        calls++
        if (calls === 1) {
          return HttpResponse.json(
            { status: 409, code: 'import.concurrent', detail: 'Tente de novo.' },
            { status: 409 },
          )
        }
        return HttpResponse.json({ created: 0, confirmed: 0, skipped: 1 })
      }),
    )
    renderImport()

    await chooseFile(ofx([['-10.00', 'PADARIA PAO QUENTE']]))
    await preview()
    fireEvent.click(importButton())
    fireEvent.click(await screen.findByRole('button', { name: 'Tentar de novo' }))

    expect(
      await screen.findByText('0 entraram, 0 confirmaram um previsto, 1 já existia'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tentar de novo' })).toBeNull()
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

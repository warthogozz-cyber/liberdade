import { cleanup, fireEvent, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { InvestmentSimulatorPage } from './InvestmentSimulatorPage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function render(indicators = true) {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { ventures: true, indicators }
  return renderWithProviders(<InvestmentSimulatorPage />, {
    path: '/mais/projetos/investimentos/simulador',
  })
}

const name = (n: number) => screen.getByRole('textbox', { name: `Nome do cenário ${n}` })
const rate = (n: number) => screen.getByRole('textbox', { name: `Taxa ao ano (%) do cenário ${n}` })

function fillBasics() {
  fireEvent.change(screen.getByLabelText('Valor inicial (R$)'), { target: { value: '5.000' } })
  fireEvent.change(screen.getByLabelText('Aporte mensal (R$)'), { target: { value: '200' } })
  fireEvent.change(screen.getByLabelText('Prazo (anos, de 1 a 50)'), { target: { value: '10' } })
}

describe('InvestmentSimulatorPage', () => {
  it('showsTheDisclaimerAndNote', async () => {
    render()
    expect(screen.getByRole('note')).toHaveTextContent(
      'Conteúdo educativo. Não é recomendação de investimento.',
    )
    await screen.findByDisplayValue('15,00')
    expect(screen.queryByText(/Simulação com taxa constante/)).not.toBeInTheDocument()

    fillBasics()
    fireEvent.click(screen.getByRole('button', { name: 'Simular' }))

    expect(
      await screen.findByText(
        'Simulação com taxa constante, sem imposto de renda nem taxas. Não é promessa de rendimento.',
      ),
    ).toBeVisible()
    expect(screen.getByRole('heading', { level: 3, name: 'Selic' })).toBeVisible()
    expect(screen.getByRole('heading', { level: 3, name: 'CDI' })).toBeVisible()
  })

  it('prefillsSelicAndCdi', async () => {
    render()

    await screen.findByDisplayValue('15,00')
    expect(name(1)).toHaveValue('Selic')
    expect(rate(1)).toHaveValue('15,00')
    expect(name(2)).toHaveValue('CDI')
    expect(rate(2)).toHaveValue('14,90')
    expect(screen.getAllByText(/^Fonte:/)).toHaveLength(2)
  })

  it('editingDropsTheStamp', async () => {
    render()
    await screen.findByDisplayValue('15,00')

    fireEvent.change(rate(1), { target: { value: '12' } })

    expect(screen.getAllByText(/^Fonte:/)).toHaveLength(1)
    expect(screen.getByText('valor informado por você')).toBeVisible()
    expect(name(2)).toHaveValue('CDI')
    expect(rate(2)).toHaveValue('14,90')
  })

  it('emptyWithoutIndicators', async () => {
    render(false)

    await screen.findByRole('textbox', { name: 'Nome do cenário 1' })
    expect(name(1)).toHaveValue('')
    expect(rate(1)).toHaveValue('')
    expect(screen.queryByRole('textbox', { name: 'Nome do cenário 2' })).not.toBeInTheDocument()
    expect(screen.queryByText(/^Fonte:/)).not.toBeInTheDocument()
  })

  it('formatsMillions', async () => {
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/ventures/investment-simulation`, () =>
        HttpResponse.json({
          scenarios: [
            {
              name: 'Selic',
              finalBalance: { amount: '7298018.76', currency: 'BRL' },
              totalContributed: { amount: '1200000.00', currency: 'BRL' },
              earnings: { amount: '6098018.76', currency: 'BRL' },
            },
          ],
        }),
      ),
    )
    render()
    await screen.findByDisplayValue('15,00')
    fillBasics()

    fireEvent.click(screen.getByRole('button', { name: 'Simular' }))

    expect(await screen.findByText(/R\$\s7\.298\.018,76/)).toBeVisible()
    expect(screen.getByText(/R\$\s1\.200\.000,00/)).toBeVisible()
    expect(screen.getByText(/R\$\s6\.098\.018,76/)).toBeVisible()
  })

  it('atMostThreeScenarios', async () => {
    render()
    await screen.findByDisplayValue('15,00')

    fireEvent.click(screen.getByRole('button', { name: 'Acrescentar cenário' }))

    expect(name(3)).toHaveValue('')
    expect(screen.queryByRole('button', { name: 'Acrescentar cenário' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Remover cenário 3' }))
    expect(screen.getByRole('button', { name: 'Acrescentar cenário' })).toBeVisible()
  })

  it('invalidFieldsShowErrorsAndSendNothing', async () => {
    let calls = 0
    server.use(
      http.post(`${API_URL}/v1/workspaces/:ws/ventures/investment-simulation`, () => {
        calls += 1
        return HttpResponse.json({ scenarios: [] })
      }),
    )
    render()
    await screen.findByDisplayValue('15,00')
    fireEvent.change(screen.getByLabelText('Prazo (anos, de 1 a 50)'), { target: { value: '51' } })

    fireEvent.click(screen.getByRole('button', { name: 'Simular' }))

    const years = screen.getByLabelText('Prazo (anos, de 1 a 50)')
    expect(years).toHaveAttribute('aria-invalid', 'true')
    expect(years).toHaveAccessibleDescription('Informe um número inteiro de 1 a 50.')
    expect(calls).toBe(0)
  })

  it('offlineDisablesSimulate', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    render()

    expect(await screen.findByRole('button', { name: 'Simular' })).toBeDisabled()
    expect(screen.getByText('Conecte-se para simular.')).toBeVisible()
  })
})

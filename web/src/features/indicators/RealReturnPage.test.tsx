import { act, cleanup, fireEvent, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { mockState, seedMockFamily } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { RealReturnPage } from './RealReturnPage'

afterEach(cleanup)

function render(arrange?: () => void) {
  const workspace = seedMockFamily()
  mockState.modules[workspace.id] = { indicators: true }
  arrange?.()
  return renderWithProviders(<RealReturnPage />, { path: '/mais/indicadores/calculadora' })
}

const nominalField = () => screen.getByRole('textbox', { name: 'Rendimento ao ano (%)' })
const inflationField = () => screen.getByRole('textbox', { name: 'Inflação em 12 meses (%)' })

/** Resolves once the screen has had the indicators answer (or error) and rendered it. */
async function settled(): Promise<void> {
  await screen.findByRole('note')
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 100))
  })
}

async function renderPrefilled() {
  render()
  await screen.findByDisplayValue('4,00')
}

describe('RealReturnPage', () => {
  it('showsTheDisclaimer', async () => {
    render()

    expect(await screen.findByRole('note')).toHaveTextContent(
      'Conteúdo educativo. Não é recomendação de investimento.',
    )
  })

  it('prefillsIpcaWithStamp', async () => {
    await renderPrefilled()

    expect(inflationField()).toHaveValue('4,00')
    expect(screen.getByText(/^Fonte:/)).toHaveTextContent('Fonte: Banco Central do Brasil')
    expect(nominalField()).toHaveAttribute('inputmode', 'decimal')
    expect(inflationField()).toHaveAttribute('inputmode', 'decimal')
  })

  it('editingHidesTheStamp', async () => {
    await renderPrefilled()

    fireEvent.change(inflationField(), { target: { value: '5' } })

    expect(screen.queryByText(/^Fonte:/)).not.toBeInTheDocument()
    expect(screen.getByText('valor informado por você')).toBeInTheDocument()
  })

  it('computesTheExample', async () => {
    await renderPrefilled()

    fireEvent.change(nominalField(), { target: { value: '10' } })

    const output = screen.getByRole('status')
    expect(output.tagName).toBe('OUTPUT')
    expect(output).toHaveAttribute('aria-live', 'polite')
    expect(output).toHaveTextContent('5,77%')
    expect(output).toHaveTextContent('(1 + 10,00%) ÷ (1 + 4,00%) − 1 = 5,77%')
    expect(output).not.toHaveTextContent('abaixo da inflação')
  })

  it('belowInflationWithoutAlarm', async () => {
    await renderPrefilled()

    fireEvent.change(nominalField(), { target: { value: '3' } })

    expect(screen.getByRole('status')).toHaveTextContent('−0,96%')
    expect(screen.getByText('abaixo da inflação')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('saysEqualToInflation', async () => {
    await renderPrefilled()

    fireEvent.change(nominalField(), { target: { value: '4' } })

    expect(screen.getByRole('status')).toHaveTextContent('0,00%')
    expect(screen.getByText('igual à inflação')).toBeInTheDocument()
  })

  it('outOfRangeError', async () => {
    await renderPrefilled()

    fireEvent.change(nominalField(), { target: { value: '1001' } })

    const message = screen.getByText('Use um número entre −99,99 e 1000')
    expect(nominalField()).toHaveAttribute('aria-describedby', message.id)
    expect(nominalField()).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('acceptsANegativeIpca', async () => {
    render(() => {
      const index = mockState.indicators.findIndex((item) => item.code === 'IPCA_12M')
      mockState.indicators[index] = { ...mockState.indicators[index]!, value: '-0.50' }
    })
    await screen.findByDisplayValue('−0,50')

    expect(screen.getByText(/^Fonte:/)).toBeInTheDocument()
    expect(screen.queryByText('Use um número entre −99,99 e 1000')).not.toBeInTheDocument()
    fireEvent.change(nominalField(), { target: { value: '10' } })
    expect(screen.getByRole('status')).toHaveTextContent('10,55%')
  })

  it('noIpcaLeavesFieldEmpty', async () => {
    render(() => {
      const index = mockState.indicators.findIndex((item) => item.code === 'IPCA_12M')
      mockState.indicators[index] = {
        ...mockState.indicators[index]!,
        value: null,
        referenceDate: null,
        variation: null,
        updatedAt: null,
      }
    })

    await settled()
    expect(inflationField()).toHaveValue('')
    expect(screen.queryByText(/^Fonte:/)).not.toBeInTheDocument()
  })

  it('failedQueryLeavesFieldEmpty', async () => {
    server.use(
      http.get(`${API_URL}/v1/workspaces/:ws/indicators`, () =>
        HttpResponse.json({ code: 'internal' }, { status: 500 }),
      ),
    )
    render()

    await settled()
    expect(inflationField()).toHaveValue('')
    expect(screen.queryByText(/^Fonte:/)).not.toBeInTheDocument()
  })
})

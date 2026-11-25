import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { foodSweetsVenture, seedMockFamily, seedVentureFixture } from '@/mocks/data'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import type { VentureView } from '@/shared/api/ventures'
import { renderWithProviders } from '@/shared/testing'
import { VentureForm } from './VentureForm'

const BADGE = 'exemplo: ajuste para a sua realidade'

beforeEach(() => {
  localStorage.setItem('liberdade.tips', 'on')
})

afterEach(() => {
  vi.restoreAllMocks()
  cleanup()
  localStorage.clear()
})

function exampleVenture(overrides: Partial<VentureView> = {}): VentureView {
  const base = foodSweetsVenture({ status: 'ACTIVE', ...overrides })
  return {
    ...base,
    initialItems: base.initialItems.map((item) => ({ ...item, isExample: true })),
    fixedItems: base.fixedItems.map((item) => ({ ...item, isExample: true })),
    products: base.products.map((item) => ({ ...item, isExample: true })),
  }
}

function render(venture: VentureView) {
  const workspace = seedMockFamily()
  seedVentureFixture(workspace)
  return renderWithProviders(<VentureForm workspace={workspace} venture={venture} />, {
    path: '/mais/projetos/:id/editar',
    route: `/mais/projetos/${venture.id}/editar`,
  })
}

function group(name: string) {
  return screen.getByRole('group', { name })
}

describe('VentureForm', () => {
  it('exampleBadgeAndBanner', async () => {
    render(exampleVenture())

    expect(await screen.findAllByText(BADGE)).toHaveLength(3)
    expect(screen.getByText(/Os itens com o selo “exemplo” vêm do modelo/)).toBeVisible()

    fireEvent.change(within(group('Item inicial 1')).getByLabelText('Nome'), {
      target: { value: 'Forma nova' },
    })
    expect(screen.getAllByText(BADGE)).toHaveLength(2)
    fireEvent.change(within(group('Custo fixo 1')).getByLabelText('Valor (R$)'), {
      target: { value: '70,00' },
    })
    expect(screen.getAllByText(BADGE)).toHaveLength(1)
    expect(screen.getByText(/Os itens com o selo “exemplo” vêm do modelo/)).toBeVisible()

    fireEvent.change(within(group('Produto 1')).getByLabelText('Vendas previstas por mês'), {
      target: { value: '250' },
    })
    expect(screen.queryByText(BADGE)).not.toBeInTheDocument()
    expect(
      screen.queryByText(/Os itens com o selo “exemplo” vêm do modelo/),
    ).not.toBeInTheDocument()
  })

  it('amountAboveMaxShowsFieldError', async () => {
    let saves = 0
    server.use(
      http.put(`${API_URL}/v1/workspaces/:ws/ventures/:id`, () => {
        saves += 1
        return HttpResponse.json(foodSweetsVenture())
      }),
    )
    render(exampleVenture())

    const amount = within(
      await screen.findByRole('group', { name: 'Custo fixo 1' }),
    ).getByLabelText('Valor (R$)')
    fireEvent.change(amount, { target: { value: '1.000.000.000,00' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    const error = await screen.findByText(/Informe um valor de até 999\.999\.999,99/)
    expect(amount).toHaveAttribute('aria-invalid', 'true')
    expect(amount.getAttribute('aria-describedby')).toContain(error.id)
    expect(saves).toBe(0)
  })

  it('zeroAmountAndZeroPriceShowFieldErrors', async () => {
    let saves = 0
    server.use(
      http.put(`${API_URL}/v1/workspaces/:ws/ventures/:id`, () => {
        saves += 1
        return HttpResponse.json(foodSweetsVenture())
      }),
    )
    render(exampleVenture())

    const amount = within(
      await screen.findByRole('group', { name: 'Custo fixo 1' }),
    ).getByLabelText('Valor (R$)')
    fireEvent.change(amount, { target: { value: '0,00' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    const error = await screen.findByText('Use um valor maior que zero.')
    expect(amount).toHaveAttribute('aria-invalid', 'true')
    expect(amount.getAttribute('aria-describedby')).toContain(error.id)
    expect(saves).toBe(0)
  })

  it('zeroUnitCostIsAllowed', async () => {
    render(exampleVenture())

    const product = await screen.findByRole('group', { name: 'Produto 1' })
    const cost = within(product).getByLabelText('Custo por unidade (R$)')
    fireEvent.change(cost, { target: { value: '0,00' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await waitFor(() => expect(cost).not.toHaveAttribute('aria-invalid', 'true'))
    expect(screen.queryByText('Use um valor maior que zero.')).not.toBeInTheDocument()
  })

  it('conflictKeepsTheDraft', async () => {
    // The mock has version 0: a project opened at version 3 is a stale one.
    render(exampleVenture({ version: 3 }))

    const name = await screen.findByLabelText('Nome do projeto')
    fireEvent.change(name, { target: { value: 'Meu doce' } })
    fireEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    const dialog = await screen.findByRole('alertdialog')
    expect(within(dialog).getByText('O projeto mudou')).toBeVisible()
    expect(within(dialog).getByRole('button', { name: 'Recarregar' })).toBeVisible()
    expect(screen.getByLabelText('Nome do projeto')).toHaveValue('Meu doce')
  })

  it('offlineDisablesSave', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    render(exampleVenture())

    expect(await screen.findByRole('button', { name: 'Salvar' })).toBeDisabled()
    expect(screen.getByText(/conecte-se para ver as contas/i)).toBeVisible()
  })

  it('addsAndRemovesRows', async () => {
    render(exampleVenture())

    fireEvent.click(await screen.findByRole('button', { name: 'Acrescentar custo fixo' }))
    expect(group('Custo fixo 2')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Remover Custo fixo 2' }))
    await waitFor(() => expect(screen.queryByRole('group', { name: 'Custo fixo 2' })).toBeNull())
  })

  it('showsThePreviewFromTheSimulation', async () => {
    render(exampleVenture())

    const panel = await screen.findByRole('region', { name: 'Prévia das contas' })
    expect(await within(panel).findByText('710,0%')).toBeVisible()
  })

  it('keepsFocusedFieldsAboveTheMobilePanel', () => {
    render(exampleVenture())

    // The fixed panel takes up to 45vh on a phone: the page pads and scrolls past all of it.
    const panel = screen.getByRole('region', { name: 'Prévia das contas' }).parentElement
    expect(panel).toHaveClass('fixed', 'max-h-[45vh]', 'lg:sticky')
    expect(screen.getByRole('main')).toHaveClass('pb-[50vh]', 'lg:pb-6')
    expect(screen.getByLabelText('Nome do projeto').getAttribute('style')).toContain(
      'scroll-margin-bottom: 50vh',
    )
    expect(
      screen.getByRole('button', { name: 'Acrescentar custo fixo' }).getAttribute('style'),
    ).toContain('scroll-margin-bottom: 50vh')
  })
})

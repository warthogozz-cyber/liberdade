import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { PlanHub } from './PlanHub'

afterEach(cleanup)

describe('PlanHub', () => {
  it('adult sees four entries', async () => {
    seedMockFamily()
    renderWithProviders(<PlanHub />, { path: '/planejar' })

    expect(await screen.findByRole('link', { name: /Orçamento/ })).toHaveAttribute(
      'href',
      '/planejar/orcamento',
    )
    expect(screen.getByRole('link', { name: /Limites por categoria no mês/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Metas/ })).toHaveAttribute('href', '/planejar/metas')
    expect(screen.getByRole('link', { name: /Reserva/ })).toHaveAttribute(
      'href',
      '/planejar/reserva',
    )
    expect(screen.getByRole('link', { name: /Recorrências/ })).toHaveAttribute(
      'href',
      '/planejar/recorrencias',
    )
    expect(screen.getAllByRole('link')).toHaveLength(4)
  })

  it('youth sees goals and recurrences only', async () => {
    const workspace = seedMockFamily()
    workspace.role = 'YOUTH'
    renderWithProviders(<PlanHub />, { path: '/planejar' })

    expect(await screen.findByRole('link', { name: /Metas/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Recorrências/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Orçamento/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Reserva/ })).not.toBeInTheDocument()
  })
})

import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { seedMockFamily } from '@/mocks/data'
import { renderWithProviders } from '@/shared/testing'
import { InvestmentsPage } from './InvestmentsPage'

afterEach(cleanup)

describe('InvestmentsPage', () => {
  it('showsTheDisclaimer', () => {
    seedMockFamily()
    renderWithProviders(<InvestmentsPage />, { path: '/mais/projetos/investimentos' })

    expect(screen.getByRole('note')).toHaveTextContent(
      'Conteúdo educativo. Não é recomendação de investimento.',
    )
  })

  it('listsTheFiveTopicsInOrderWithTheReviewDate', () => {
    seedMockFamily()
    renderWithProviders(<InvestmentsPage />, { path: '/mais/projetos/investimentos' })

    const names = screen.getAllByRole('heading', { level: 2 }).map((each) => each.textContent)
    expect(names).toEqual(['Poupança', 'Renda fixa', 'Tesouro Direto', 'Fundos', 'Ações'])
    expect(screen.getByText('Revisado em 25/11/2026')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Simular juros compostos' })).toHaveAttribute(
      'href',
      '/mais/projetos/investimentos/simulador',
    )
  })
})

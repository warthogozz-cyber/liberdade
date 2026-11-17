import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { renderWithProviders } from '@/shared/testing'
import { TermsPage } from './TermsPage'

afterEach(cleanup)

describe('TermsPage', () => {
  it('hasCvmNotice', () => {
    renderWithProviders(<TermsPage />, { path: '/termos' })

    expect(screen.getByText(/educativo e informativo/)).toBeVisible()
    expect(screen.getByText(/não faz recomendação personalizada de investimento/)).toBeVisible()
    expect(screen.getByText(/credenciados pela CVM/)).toBeVisible()
    expect(screen.getByText(/terms-2026-11/)).toBeVisible()
  })

  it('followsTheOrderOfTheTerms', () => {
    renderWithProviders(<TermsPage />, { path: '/termos' })

    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual([
      'O que é o Liberdade?',
      'É grátis e de código aberto',
      'O conteúdo é educativo, não é recomendação de investimento',
      'O que é responsabilidade da família',
      'O serviço é oferecido como está',
      'Como excluir a conta',
      'Lei aplicável e contato',
    ])
  })
})

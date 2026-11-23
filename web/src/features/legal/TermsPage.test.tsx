import { cleanup, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { TermsPage } from './TermsPage'

afterEach(cleanup)

function renderTerms() {
  return renderWithProviders(<TermsPage />, { path: '/termos' })
}

const headings = () => screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)

describe('TermsPage', () => {
  it('hasCvmNotice', async () => {
    renderTerms()

    expect(screen.getByText(/educativo e informativo/)).toBeVisible()
    expect(screen.getByText(/não faz recomendação personalizada de investimento/)).toBeVisible()
    expect(screen.getByText(/credenciados pela CVM/)).toBeVisible()
    expect(await screen.findByText(/terms-2026-12/)).toBeVisible()
  })

  it('followsTheOrderOfTheTerms', () => {
    renderTerms()

    expect(headings()).toEqual([
      'O que é o Liberdade?',
      'O plano Grátis é grátis, e o código é aberto',
      'O que é a Família Fundadora?',
      'O que a Fundadora inclui hoje',
      'Desistiu? Reembolso integral em 7 dias',
      'Sem renovação automática',
      'O conteúdo é educativo, não é recomendação de investimento',
      'O que é responsabilidade da família',
      'O serviço é oferecido como está',
      'Como excluir a conta',
      'Lei aplicável e contato',
    ])
  })

  it('statesThePresaleHonestly', () => {
    renderTerms()

    expect(
      screen.getByText(/pré-venda do plano Família: você paga 1 ano adiantado, R\$ 89/),
    ).toBeVisible()
    expect(screen.getByText(/só aparecem na tela Plano e no Retrato do mês/)).toBeVisible()
    expect(screen.getByText(/selo "Família Fundadora"/)).toBeVisible()
    expect(screen.getByText(/sem data prometida/)).toBeVisible()
    expect(screen.getByText(/art\. 49/)).toBeVisible()
    expect(screen.getByText(/não se renova sozinho/)).toBeVisible()
    expect(screen.getAllByText(/privacidade@liberdadefamilia\.com\.br/).length).toBeGreaterThan(0)
  })

  it('showsTheVersionTheApiSays', async () => {
    server.use(
      http.get(`${API_URL}/v1/legal/current`, () =>
        HttpResponse.json({ terms: 'terms-2026-11', privacy: 'privacy-2026-11' }),
      ),
    )
    renderTerms()

    expect(
      await screen.findByText(/Versão terms-2026-11 · atualizada em novembro de 2026/),
    ).toBeVisible()
    expect(headings()).toContain('É grátis e de código aberto')
    expect(headings()).not.toContain('O que é a Família Fundadora?')
  })

  it('showsTheLatestWithoutAnAnswer', async () => {
    let asked = false
    server.use(
      http.get(`${API_URL}/v1/legal/current`, () => {
        asked = true
        return HttpResponse.error()
      }),
    )
    renderTerms()

    await waitFor(() => expect(asked).toBe(true))
    expect(
      await screen.findByText(/Versão terms-2026-12 · atualizada em dezembro de 2026/),
    ).toBeVisible()
    expect(headings()).toContain('O que é a Família Fundadora?')
  })
})

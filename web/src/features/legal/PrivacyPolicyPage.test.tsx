import { cleanup, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/mocks/server'
import { API_URL } from '@/shared/api'
import { renderWithProviders } from '@/shared/testing'
import { PrivacyPolicyPage } from './PrivacyPolicyPage'

afterEach(() => {
  cleanup()
  vi.unstubAllEnvs()
})

function renderPolicy() {
  return renderWithProviders(<PrivacyPolicyPage />, { path: '/privacidade' })
}

function answerNovember() {
  server.use(
    http.get(`${API_URL}/v1/legal/current`, () =>
      HttpResponse.json({ terms: 'terms-2026-11', privacy: 'privacy-2026-11.1' }),
    ),
  )
}

const REQUIRED_SECTIONS = [
  'Quem cuida dos seus dados?',
  'Que dados guardamos e para quê?',
  'Em que a lei nos autoriza a tratar esses dados?',
  'E os dados de crianças e adolescentes?',
  'Com quem os dados são tratados?',
  'Por quanto tempo guardamos?',
  'Quais são os seus direitos e como usá-los?',
  'Como protegemos os dados?',
  'E se esta política mudar?',
]

describe('PrivacyPolicyPage', () => {
  it('showsControllerAndContact', () => {
    vi.stubEnv('VITE_PRIVACY_CONTROLLER', '')
    vi.stubEnv('VITE_PRIVACY_CONTACT', '')
    renderPolicy()

    expect(
      screen.getByText(/O controlador dos dados é o mantenedor do projeto Liberdade\./),
    ).toBeVisible()
    expect(screen.getAllByText(/privacidade@liberdadefamilia\.com\.br/).length).toBeGreaterThan(0)
  })

  it('showsConfiguredControllerAndContact', () => {
    vi.stubEnv('VITE_PRIVACY_CONTROLLER', 'Instituto Exemplo')
    vi.stubEnv('VITE_PRIVACY_CONTACT', 'contato@example.com')
    renderPolicy()

    expect(screen.getByText(/O controlador dos dados é Instituto Exemplo\./)).toBeVisible()
    expect(screen.getAllByText(/contato@example\.com/).length).toBeGreaterThan(0)
    expect(screen.queryByText(/mantenedor do projeto/)).not.toBeInTheDocument()
  })

  it('coversRequiredSections', async () => {
    renderPolicy()

    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual(
      REQUIRED_SECTIONS,
    )
    expect(await screen.findByText(/privacy-2026-12/)).toBeVisible()
    expect(screen.getByRole('heading', { level: 1, name: 'Política de privacidade' })).toBeVisible()
  })

  it('statesTheRetentionPeriodsOfTheDataModel', () => {
    renderPolicy()

    expect(screen.getByText(/30 dias de carência/)).toBeVisible()
    expect(screen.getByText(/Convites usados ou vencidos: apagados após 90 dias/)).toBeVisible()
    expect(screen.getByText('Trilha de atividade: 12 meses.')).toBeVisible()
    expect(screen.getByText(/Cópias de segurança do banco: somem em até 7 dias/)).toBeVisible()
    expect(screen.getByText(/Registros técnicos do servidor: até 30 dias/)).toBeVisible()
  })

  it('statesTheBillingDataItsBasesAndPeriods', () => {
    renderPolicy()

    expect(screen.getByText(/Asaas: o pagamento do plano Família Fundadora/)).toBeVisible()
    expect(screen.getByText(/Contamos com quatro operadores/)).toBeVisible()
    expect(screen.getByText(/Pedido pago: 5 anos depois do pagamento/)).toBeVisible()
    expect(screen.getByText(/Pedido nunca pago \(cancelado ou vencido\): 180 dias/)).toBeVisible()
    expect(screen.getByText(/Plano da família: enquanto a família existir/)).toBeVisible()
    expect(
      screen.getByText(/Registro dos avisos de pagamento, sem o conteúdo: 180 dias/),
    ).toBeVisible()
    expect(screen.getByText(/^Legítimo interesse:/)).toBeVisible()
    expect(screen.getByText(/o Liberdade não pede nem guarda esses dados/)).toBeVisible()
    expect(
      screen.getByText(
        /menos os pedidos de compra e o registro dos avisos de pagamento\. Se a conta de quem comprou for excluída, os pedidos perdem a ligação com essa pessoa; se a família for excluída, perdem a ligação com ela/,
      ),
    ).toBeVisible()
    expect(screen.getByText(/ficam o produto, o valor, as datas/)).toBeVisible()
  })

  it('showsTheVersionTheApiSays', async () => {
    answerNovember()
    renderPolicy()

    expect(
      await screen.findByText(/Versão privacy-2026-11\.1 · atualizada em 23 de novembro de 2026/),
    ).toBeVisible()
    expect(screen.getByText(/Contamos com três operadores/)).toBeVisible()
    expect(screen.queryByText(/Asaas/)).not.toBeInTheDocument()
  })

  it('namesTheNewDataInTheUpdatedPolicy', async () => {
    renderPolicy()

    expect(
      await screen.findByText(/tags, forma de pagamento, recorrências e lançamentos previstos/),
    ).toBeVisible()
  })

  it('stillShowsTheAcceptedTextWhenTheApiAsksForIt', async () => {
    server.use(
      http.get(`${API_URL}/v1/legal/current`, () =>
        HttpResponse.json({ terms: 'terms-2026-11', privacy: 'privacy-2026-11' }),
      ),
    )
    renderPolicy()

    expect(
      await screen.findByText(/Versão privacy-2026-11 · atualizada em novembro de 2026/),
    ).toBeVisible()
    expect(
      screen.queryByText(/tags, forma de pagamento, recorrências e lançamentos previstos/),
    ).not.toBeInTheDocument()
  })

  it('showsTheLatestWithoutAnAnswer', async () => {
    let asked = false
    server.use(
      http.get(`${API_URL}/v1/legal/current`, () => {
        asked = true
        return HttpResponse.error()
      }),
    )
    renderPolicy()

    await waitFor(() => expect(asked).toBe(true))
    expect(
      await screen.findByText(/Versão privacy-2026-12.4 · atualizada em 24 de novembro de 2026/),
    ).toBeVisible()
  })
})

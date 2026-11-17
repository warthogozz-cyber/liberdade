import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@/shared/testing'
import { PrivacyPolicyPage } from './PrivacyPolicyPage'

afterEach(() => {
  cleanup()
  vi.unstubAllEnvs()
})

function renderPolicy() {
  return renderWithProviders(<PrivacyPolicyPage />, { path: '/privacidade' })
}

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

  it('coversRequiredSections', () => {
    renderPolicy()

    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual([
      'Quem cuida dos seus dados?',
      'Que dados guardamos e para quê?',
      'Em que a lei nos autoriza a tratar esses dados?',
      'E os dados de crianças e adolescentes?',
      'Com quem os dados são tratados?',
      'Por quanto tempo guardamos?',
      'Quais são os seus direitos e como usá-los?',
      'Como protegemos os dados?',
      'E se esta política mudar?',
    ])
    expect(screen.getByText(/privacy-2026-11/)).toBeVisible()
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
})

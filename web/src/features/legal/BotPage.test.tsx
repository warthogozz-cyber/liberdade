import { cleanup, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { renderWithProviders } from '@/shared/testing'
import { BotPage } from './BotPage'

afterEach(cleanup)

function renderBot() {
  return renderWithProviders(<BotPage />, { path: '/robo' })
}

describe('BotPage', () => {
  it('explainsTheRobot', () => {
    renderBot()

    expect(
      screen.getByRole('heading', { level: 1, name: 'Robô de indicadores do Liberdade' }),
    ).toBeVisible()
    const shown = document.body.textContent ?? ''
    for (const text of ['api.bcb.gov.br', 'olinda.bcb.gov.br', '8h30', '19h30']) {
      expect(shown).toContain(text)
    }
  })

  it('answersTheFourQuestions', () => {
    renderBot()

    for (const question of [
      'O que o robô coleta?',
      'De onde vêm os dados?',
      'Quando ele passa?',
      'Como falar com o mantenedor?',
    ]) {
      expect(screen.getByRole('heading', { level: 2, name: question })).toBeVisible()
    }
    expect(screen.getByText(/Nenhum dado seu ou da sua família sai nessa busca/)).toBeVisible()
  })

  it('linksToThePrivacyPolicyForContact', () => {
    renderBot()

    expect(screen.getByRole('link', { name: /política de privacidade/i })).toHaveAttribute(
      'href',
      '/privacidade',
    )
  })

  it('listsTheNewCollections', () => {
    renderBot()

    const shown = document.body.textContent ?? ''
    expect(shown).toContain('Geofabrik')
    expect(shown).toContain('GitHub Actions')
    expect(shown).toContain('nominatim.openstreetmap.org')
    expect(shown).toMatch(/oEmbed do Instagram/)
  })

  it('showsOsmAttribution', () => {
    renderBot()

    expect(screen.getByRole('heading', { level: 2, name: 'Dados de terceiros' })).toBeVisible()
    const shown = document.body.textContent ?? ''
    expect(shown).toContain('© OpenStreetMap contributors')
    expect(shown).toContain('ODbL')
    expect(screen.getByRole('link', { name: 'openstreetmap.org/copyright' })).toHaveAttribute(
      'href',
      'https://www.openstreetmap.org/copyright',
    )
  })
})

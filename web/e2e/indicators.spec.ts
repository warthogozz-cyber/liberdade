import type { Page } from '@playwright/test'
import { createFamily, expect, newUser, signInWithGoogle, test } from './support.ts'

const DISCLAIMER = 'Conteúdo educativo. Não é recomendação de investimento.'
// The startup collection runs a few seconds after the API is up: the first look may be empty.
const COLLECTION = { timeout: 30_000 }

/** The Dono turns Indicadores on in Mais › Módulos. */
async function turnIndicatorsOn(page: Page): Promise<void> {
  await page.goto('/mais/modulos')
  const toggle = page.getByRole('switch', { name: 'Indicadores' })
  await expect(toggle).toHaveAttribute('aria-checked', 'false')
  await toggle.click()
  await expect(toggle).toHaveAttribute('aria-checked', 'true')
}

/** Opens the screen the way a person does: Mais, then the Indicadores item. */
async function openIndicators(page: Page): Promise<void> {
  await page.goto('/mais')
  await page.getByRole('link', { name: 'Indicadores' }).click()
  await expect(page).toHaveURL('/mais/indicadores')
  await expect(page.getByRole('heading', { level: 1, name: 'Indicadores' })).toBeVisible()
}

test('fiveCardsWithSource: the screen shows five indicators, each with its source and date', async ({
  page,
}) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await turnIndicatorsOn(page)
  await openIndicators(page)

  const cards = page.getByRole('main').getByRole('listitem')
  await expect(cards).toHaveCount(5)
  await expect(cards.filter({ hasText: '15,00% a.a.' })).toHaveCount(1, COLLECTION)
  for (const card of await cards.all()) {
    await expect(card).toContainText('Fonte: Banco Central do Brasil', COLLECTION)
    await expect(card).toContainText('atualizado em', COLLECTION)
  }
  await expect(cards.filter({ hasText: 'Selic' })).toContainText('15,00% a.a.')
})

test('disclaimerOnBothScreens: the CVM notice shows on the screen and on the calculator', async ({
  page,
}) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await turnIndicatorsOn(page)
  await openIndicators(page)
  await expect(page.getByText(DISCLAIMER)).toBeVisible()

  await page.getByRole('link', { name: 'Calcular rendimento real' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Rendimento real' })).toBeVisible()
  await expect(page.getByText(DISCLAIMER)).toBeVisible()
})

test('calculatorExample: the inflation comes filled in and 10% gives 5,77%', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await turnIndicatorsOn(page)
  await openIndicators(page)

  await page.getByRole('link', { name: 'Calcular rendimento real' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Rendimento real' })).toBeVisible()
  await expect(page.getByLabel('Inflação em 12 meses (%)')).toHaveValue('4,00', COLLECTION)
  await page.getByLabel('Rendimento ao ano (%)').fill('10')
  await expect(page.getByRole('main').getByText('5,77%', { exact: true })).toBeVisible()
})

test('offHidesTheItem: with the module off Mais has no item and the address goes home', async ({
  page,
}) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await turnIndicatorsOn(page)
  await page.goto('/mais')
  await expect(page.getByRole('link', { name: 'Indicadores' })).toBeVisible()

  await page.goto('/mais/modulos')
  const toggle = page.getByRole('switch', { name: 'Indicadores' })
  await toggle.click()
  await expect(toggle).toHaveAttribute('aria-checked', 'false')

  await page.goto('/mais')
  await expect(page.getByRole('button', { name: 'Sair' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Indicadores' })).toHaveCount(0)
  await page.goto('/mais/indicadores')
  await expect(page).toHaveURL('/inicio')
})

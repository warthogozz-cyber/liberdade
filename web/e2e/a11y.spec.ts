import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Locator, type Page } from '@playwright/test'
import { createFamily, newUser, signInWithGoogle } from './support.ts'

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

/**
 * Checks the page against WCAG 2.2 AA in the light and dark themes, once its `<h1>` and the content
 * that `ready` points at are on the screen.
 */
async function expectNoViolations(page: Page, heading: string, ready: Locator): Promise<void> {
  for (const colorScheme of ['light', 'dark'] as const) {
    await page.emulateMedia({ colorScheme })
    await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
    await expect(ready).toBeVisible()
    const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()
    expect(results.violations, `${page.url()} (${colorScheme})`).toEqual([])
  }
}

test('/entrar meets WCAG 2.2 AA in both themes', async ({ page }) => {
  await page.goto('/entrar')

  await expectNoViolations(
    page,
    'Liberdade',
    page.getByRole('button', { name: 'Entrar com Google' }),
  )
})

test('/boas-vindas meets WCAG 2.2 AA in both themes', async ({ page }) => {
  await signInWithGoogle(page, newUser())

  await expectNoViolations(page, 'Olá, Ana! 👋', page.getByRole('button', { name: 'Começar' }))
})

test('the family screens meet WCAG 2.2 AA in both themes', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  const screens: { path: string; heading: string; ready: Locator }[] = [
    {
      path: '/lancar',
      heading: 'Novo gasto',
      ready: page.getByRole('radio', { name: '🛒 Mercado' }),
    },
    { path: '/inicio', heading: 'Início', ready: page.getByText('Saldo total') },
    {
      path: '/lancamentos',
      heading: 'Lançamentos',
      ready: page.getByText('Nenhum lançamento neste mês.'),
    },
    { path: '/mais', heading: 'Mais', ready: page.getByRole('button', { name: 'Sair' }) },
  ]

  for (const { path, heading, ready } of screens) {
    await page.goto(path)
    await expectNoViolations(page, heading, ready)
  }
})

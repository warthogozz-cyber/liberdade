import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Locator, type Page } from '@playwright/test'
import {
  createFamily,
  newUser,
  recordExpense,
  signInWithGoogle,
  waitForQueueToEmpty,
} from './support.ts'

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

/**
 * Checks the page against WCAG 2.2 AA in the light and dark themes, once its `<h1>` and the content
 * that `ready` points at are on the screen.
 */
async function expectNoViolations(page: Page, heading: string, ready: Locator): Promise<void> {
  await expectNoViolationsWith(page, ready, () =>
    expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible(),
  )
}

/** The same check with a sheet or dialog open, which hides the page's own heading from the tree. */
async function expectNoViolationsOpen(page: Page, ready: Locator): Promise<void> {
  await expectNoViolationsWith(page, ready, async () => {})
}

async function expectNoViolationsWith(
  page: Page,
  ready: Locator,
  ensureHeading: () => Promise<void>,
): Promise<void> {
  for (const colorScheme of ['light', 'dark'] as const) {
    await page.emulateMedia({ colorScheme })
    await ensureHeading()
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

test('the screens to view and organize meet WCAG 2.2 AA in both themes', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await recordExpense(page, '1234', '🛒 Mercado')
  await waitForQueueToEmpty(page)

  await expectNoViolations(
    page,
    'Lançamentos',
    page.getByRole('main').getByRole('listitem').filter({ hasText: 'Mercado' }),
  )
  await page.getByRole('main').getByRole('listitem').filter({ hasText: 'Mercado' }).click()
  await expectNoViolationsOpen(page, page.getByRole('link', { name: 'Editar' }))
  await page.keyboard.press('Escape')

  await page.goto('/mais/contas')
  await page.getByRole('button', { name: 'Nova conta' }).click()
  await expectNoViolationsOpen(page, page.getByRole('dialog', { name: 'Nova conta' }))
  await page.keyboard.press('Escape')

  await page.goto('/mais/categorias')
  await page.getByRole('button', { name: 'Nova categoria' }).click()
  await expectNoViolationsOpen(page, page.getByRole('dialog', { name: 'Nova categoria' }))
  await page.keyboard.press('Escape')

  await page.goto('/painel')
  await page.getByRole('group').filter({ hasText: 'Ver tabela' }).first().click()
  await expectNoViolations(
    page,
    'Painel do mês',
    page.getByRole('table', { name: 'Gastos por categoria' }),
  )

  // A transfer needs two accounts: the family starts with one.
  await page.goto('/mais/contas')
  await page.getByRole('button', { name: 'Nova conta' }).click()
  await page
    .getByRole('dialog', { name: 'Nova conta' })
    .getByLabel('Nome', { exact: true })
    .fill('Corrente')
  await page
    .getByRole('dialog', { name: 'Nova conta' })
    .getByRole('button', { name: 'Salvar' })
    .click()
  await expect(page.getByRole('button', { name: /Corrente/ })).toBeVisible()

  await page.goto('/lancar?tipo=transferencia')
  await expectNoViolations(page, 'Nova transferência', page.getByRole('group', { name: 'Para' }))
})

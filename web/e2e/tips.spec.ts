import {
  CONTEXT_OPTIONS,
  createFamily,
  expect,
  newUser,
  setModules,
  signInWithGoogle,
  test,
  withContext,
} from './support.ts'

const tipsValue = (page: import('@playwright/test').Page) =>
  page.evaluate(() => localStorage.getItem('liberdade.tips'))

test.describe('tips preference in the fixture', () => {
  test('the default context has the tips off', async ({ page }) => {
    await page.goto('/entrar')
    expect(await tipsValue(page)).toBe('off')
  })

  test('a withContext page has the tips off', async ({ browser }) => {
    await withContext(browser, async (_context, page) => {
      await page.goto('/entrar')
      expect(await tipsValue(page)).toBe('off')
    })
  })

  test('a browser.newContext page has the tips off', async ({ browser }) => {
    const context = await browser.newContext(CONTEXT_OPTIONS)
    try {
      const page = await context.newPage()
      await page.goto('/entrar')
      expect(await tipsValue(page)).toBe('off')
    } finally {
      await context.close()
    }
  })
})

test.describe('with tips on', () => {
  test.use({ tips: true })

  test('the default context leaves the tips on', async ({ page }) => {
    await page.goto('/entrar')
    expect(await tipsValue(page)).toBeNull()
  })

  test('a withContext page leaves the tips on', async ({ browser }) => {
    await withContext(browser, async (_context, page) => {
      await page.goto('/entrar')
      expect(await tipsValue(page)).toBeNull()
    })
  })

  test('a browser.newContext page leaves the tips on', async ({ browser }) => {
    const context = await browser.newContext(CONTEXT_OPTIONS)
    try {
      const page = await context.newPage()
      await page.goto('/entrar')
      expect(await tipsValue(page)).toBeNull()
    } finally {
      await context.close()
    }
  })
})

const TRANSACTIONS_TIP = 'Todos os lançamentos do mês. Toque em um para editar ou excluir.'
const DASHBOARD_TIP = 'Aqui está o mês da família: quanto entrou, quanto saiu e para onde foi.'

test.describe('first visit tips and tours', () => {
  test.use({ tips: true })

  test('a tip shows once', async ({ page }) => {
    await signInWithGoogle(page, newUser())
    await createFamily(page)

    await page.goto('/lancamentos')
    await expect(page.getByText(TRANSACTIONS_TIP)).toBeVisible()
    await page.getByRole('button', { name: 'Entendi' }).click()
    await expect(page.getByText(TRANSACTIONS_TIP)).toBeHidden()

    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: 'Lançamentos' })).toBeVisible()
    await expect(page.getByText(TRANSACTIONS_TIP)).toHaveCount(0)
  })

  test('the switch in Ajustes turns the tips and the tours off', async ({ page }) => {
    await signInWithGoogle(page, newUser())
    await createFamily(page)
    await setModules(page, true)

    // With the tips on, the Painel do mês has its tip. Seeing it does not dismiss it.
    await page.goto('/painel')
    await expect(page.getByRole('heading', { level: 1, name: 'Painel do mês' })).toBeVisible()
    await expect(page.getByText(DASHBOARD_TIP)).toBeVisible()

    await page.goto('/mais/ajustes')
    const tips = page.getByRole('switch', { name: 'Mostrar dicas' })
    await expect(tips).toHaveAttribute('aria-checked', 'true')
    await tips.click()
    await expect(tips).toHaveAttribute('aria-checked', 'false')

    await page.goto('/painel')
    await expect(page.getByRole('heading', { level: 1, name: 'Painel do mês' })).toBeVisible()
    await expect(page.getByText(DASHBOARD_TIP)).toHaveCount(0)

    await page.goto('/planejar')
    await expect(page.getByRole('heading', { level: 1, name: 'Planejar' })).toBeVisible()
    await expect(page.getByRole('link', { name: /^Orçamento/ })).toBeVisible()
    await expect(page.getByRole('dialog')).toHaveCount(0)
  })

  test('the tour works from the keyboard', async ({ page }) => {
    await signInWithGoogle(page, newUser())
    await createFamily(page)
    await setModules(page, true)

    // After a module toggle a fresh load can still bounce to /inicio, so retry until the screen holds.
    await expect(async () => {
      await page.goto('/mais/conquistas')
      await expect(page.getByRole('heading', { level: 1, name: 'Conquistas' })).toBeVisible({
        timeout: 3_000,
      })
    }).toPass({ timeout: 30_000 })
    const tour = page.getByRole('dialog', { name: /Sua sequência conta as semanas/ })
    await expect(tour).toBeVisible()
    await expect(tour.getByText('Passo 1 de 2')).toBeVisible()

    // The card takes the focus when it opens, so the first Tab lands on its first button.
    await page.keyboard.press('Tab')
    await expect(tour.getByRole('button', { name: 'Pular' })).toBeFocused()

    // Esc skips it, and the focus goes back to where it was: nothing had it, so the page.
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await expect(page.locator('body')).toBeFocused()

    // Skipping counts as seen.
    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: 'Conquistas' })).toBeVisible()
    await expect(page.getByRole('heading', { level: 2, name: 'Próximas' })).toBeVisible()
    await expect(page.getByRole('dialog')).toHaveCount(0)
  })
})

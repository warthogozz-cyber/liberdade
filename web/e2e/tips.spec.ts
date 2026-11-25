import { expect, test, withContext, CONTEXT_OPTIONS } from './support.ts'

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

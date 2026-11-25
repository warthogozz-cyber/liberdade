import { expect, test } from './support.ts'

test.describe('tips preference in the fixture', () => {
  test('the fixture turns the tips off by default', async ({ page }) => {
    await page.goto('/entrar')
    expect(await page.evaluate(() => localStorage.getItem('liberdade.tips'))).toBe('off')
  })
})

test.describe('with tips on', () => {
  test.use({ tips: true })

  test('the fixture leaves the tips on', async ({ page }) => {
    await page.goto('/entrar')
    expect(await page.evaluate(() => localStorage.getItem('liberdade.tips'))).toBeNull()
  })
})

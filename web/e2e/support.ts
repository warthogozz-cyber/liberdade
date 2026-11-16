// The API allows 20 sign-in attempts per minute per IP (D10), and every request here comes from
// 127.0.0.1. Each `signInWithGoogle` counts 1 (the authorization; the callback is not counted) and
// a passkey sign-in counts 2 (the options and the login). The whole suite makes 7: 5
// `signInWithGoogle` (a11y 2, first-expense 2, passkey 1) and 1 passkey sign-in. Recount when
// adding a spec.
import { expect, type Page } from '@playwright/test'

export type E2EUser = { sub: string; claims: Record<string, string> }

/** A new person each time, so each test gets its own family without cleaning the database (D11). */
export function newUser(): E2EUser {
  return {
    sub: 'e2e-' + crypto.randomUUID(),
    claims: {
      name: 'Ana Teste',
      given_name: 'Ana',
      family_name: 'Teste',
      email: 'ana@example.com',
    },
  }
}

/** Signs in through the test provider that stands in for Google, landing on the welcome screen. */
export async function signInWithGoogle(page: Page, user: E2EUser): Promise<void> {
  await page.goto('/entrar')
  await page.getByRole('button', { name: 'Entrar com Google' }).click()
  await page.locator('[name="username"]').fill(user.sub)
  await page.locator('[name="claims"]').fill(JSON.stringify(user.claims))
  await page.getByRole('button', { name: 'Sign-in' }).click()
  await page.waitForURL('**/boas-vindas')
}

/** Creates the family with the suggested name, landing on the new expense. */
export async function createFamily(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { level: 1, name: 'Olá, Ana! 👋' })).toBeVisible()
  await page.getByRole('button', { name: 'Começar' }).click()
  await page.waitForURL('**/lancar')
}

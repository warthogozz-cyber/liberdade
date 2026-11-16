// The API allows 20 sign-in attempts per minute per IP (D10), and every request here comes from
// 127.0.0.1. Each `signInWithGoogle` counts 1 (the authorization; the callback is not counted) and
// a passkey sign-in counts 2 (the options and the login). The specs run one after another and each
// takes several seconds, so a minute holds only a few of them; recount if a spec ever signs in
// more than once in a loop.
import { expect, type Locator, type Page } from '@playwright/test'

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

/** Types an amount on the keypad: the digits of the cents, so "123456" is R$ 1.234,56. */
export async function typeAmount(page: Page | Locator, digits: string): Promise<void> {
  for (const digit of digits) await page.getByRole('button', { name: digit, exact: true }).click()
}

/** Erases `count` digits of the amount on the keypad. */
export async function eraseAmount(page: Page, count: number): Promise<void> {
  for (let i = 0; i < count; i += 1) await page.getByRole('button', { name: 'Apagar' }).click()
}

/** Picks the account of an income or expense on the date-and-account sheet. */
export async function pickAccount(page: Page, name: string): Promise<void> {
  await page.getByRole('button', { name: /^(Hoje|Ontem|\d+ de \p{L}+\.?) · .+ · Eu/u }).click()
  await page.getByRole('radio', { name, exact: true }).check()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('radio', { name, exact: true })).toBeHidden()
}

/** A formatted amount as the app shows it, tolerant of the no-break space after "R$". */
export function money(amount: string): RegExp {
  return new RegExp(String.raw`R\$\s` + amount.replace(/[.,]/g, (c) => `\\${c}`))
}

/** The toast region, where the app's notices appear. */
export function notices(page: Page): Locator {
  return page.getByRole('region', { name: 'Notificações' })
}

/** Records an expense of the given digits (cents) in a category on the default account. */
export async function recordExpense(page: Page, digits: string, category: string): Promise<void> {
  await page.goto('/lancar')
  await typeAmount(page, digits)
  await page.getByText(category, { exact: true }).click()
  await page.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText('Gasto salvo')).toBeVisible()
  await expect(page).toHaveURL('/inicio')
}

/** Opens `/lancamentos` and waits until nothing is queued any more. */
export async function waitForQueueToEmpty(page: Page): Promise<void> {
  await page.goto('/lancamentos')
  await expect(page.getByRole('main').getByRole('listitem').first()).toBeVisible()
  await expect(page.getByText('Aguardando sinal')).toHaveCount(0, { timeout: 20_000 })
}

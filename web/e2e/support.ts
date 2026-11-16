// The API allows 20 sign-in attempts per minute per IP (D10), and every request here comes from
// 127.0.0.1. Each `signInWithGoogle` counts 2 (the authorization and the callback), a passkey
// sign-in counts 2 (the options and the login) and the refused-callback test counts 1. The whole
// suite makes 13: 5 `signInWithGoogle` (a11y 2, first-expense 2, passkey 1), 1 passkey sign-in and
// 1 refused callback. Recount when adding a spec.
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

/**
 * Creates the family with the suggested name, landing on the new expense once the device has saved
 * it, so a reload or a `page.goto` afterwards finds the family.
 */
export async function createFamily(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { level: 1, name: 'Olá, Ana! 👋' })).toBeVisible()
  await page.getByRole('button', { name: 'Começar' }).click()
  await page.waitForURL('**/lancar')
  await waitForSavedFamily(page)
}

// Accounts, both kinds of categories, the month's summary and its transactions (`prefetchWorkspace`).
const FAMILY_QUERIES = 5

// The app saves the query cache to the device at most once a second. A reload sooner brings back
// the cache from before the last change, and that cache is fresh enough not to be fetched again.

/** Waits until the query cache saved on the device holds the family and its data. */
async function waitForSavedFamily(page: Page): Promise<void> {
  await expect
    .poll(async () => (await page.evaluate(readSavedCache)).familyQueries, {
      message: 'the query cache saved on the device holds the family',
      timeout: 10_000,
    })
    .toBeGreaterThanOrEqual(FAMILY_QUERIES)
}

/** Waits until the transactions in the query cache saved on the device number `count`. */
export async function waitForSavedTransactions(page: Page, count: number): Promise<void> {
  await expect
    .poll(async () => (await page.evaluate(readSavedCache)).transactions, {
      message: 'the transactions in the query cache saved on the device',
      timeout: 10_000,
    })
    .toBe(count)
}

type SavedCache = {
  clientState: {
    queries: {
      queryKey: unknown[]
      state: { status: string; data?: unknown }
    }[]
  }
}

/**
 * Runs in the page: how many of the family's queries the query cache saved on the device holds (0
 * before the family) and how many transactions it holds.
 */
async function readSavedCache(): Promise<{ familyQueries: number; transactions: number }> {
  const db = await new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open('liberdade')
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
  try {
    const row = await new Promise<{ value: string } | undefined>((resolve, reject) => {
      const request = db.transaction('kv').objectStore('kv').get('liberdade-query-cache')
      request.onsuccess = () => resolve(request.result as { value: string } | undefined)
      request.onerror = () => reject(request.error)
    })
    if (!row) return { familyQueries: 0, transactions: 0 }
    const { queries } = (JSON.parse(row.value) as SavedCache).clientState
    const me = queries.find((query) => query.queryKey[0] === 'me')
    const hasFamily =
      ((me?.state.data as { workspaces?: unknown[] } | undefined)?.workspaces ?? []).length > 0
    const family = queries.filter(
      (query) => query.queryKey[0] === 'ws' && query.state.status === 'success',
    )
    const transactions = family
      .filter((query) => query.queryKey[2] === 'transactions')
      .reduce((sum, query) => sum + (query.state.data as unknown[]).length, 0)
    return { familyQueries: hasFamily ? family.length : 0, transactions }
  } finally {
    db.close()
  }
}

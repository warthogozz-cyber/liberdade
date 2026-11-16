import { expect, test, type Page } from '@playwright/test'
import { createFamily, newUser, signInWithGoogle, waitForSavedTransactions } from './support.ts'

const S1_LIMIT_MS = 60_000
const SYNC_TIMEOUT_MS = 20_000

async function typeAmount(page: Page, keys: string[]): Promise<void> {
  for (const key of keys) await page.getByRole('button', { name: key, exact: true }).click()
}

function savedNotice(page: Page) {
  return page.getByRole('region', { name: 'Notificações' }).getByText('Gasto salvo')
}

function navigation(page: Page) {
  return page.getByRole('navigation', { name: 'Navegação principal' })
}

function row(page: Page, category: string) {
  return page.getByRole('listitem').filter({ hasText: category })
}

test('records the first expense online (S1)', async ({ page }) => {
  const start = Date.now()
  await signInWithGoogle(page, newUser())
  await expect(page.getByRole('heading', { level: 1, name: 'Olá, Ana! 👋' })).toBeVisible()
  await expect(page.getByLabel('Nome da família')).toHaveValue('Família Teste')
  await createFamily(page)

  await typeAmount(page, ['1', '2', '3', '4'])
  await page.getByText('🛒 Mercado', { exact: true }).click()
  await expect(page.getByRole('radio', { name: '🛒 Mercado' })).toBeChecked()
  await page.getByRole('button', { name: 'Salvar' }).click()

  await expect(savedNotice(page)).toBeVisible()
  await expect(page).toHaveURL('/inicio')
  expect(Date.now() - start).toBeLessThan(S1_LIMIT_MS)
  await expect(page.getByRole('main')).toContainText(/Gastos em \p{L}+\s*R\$\s12,34/u)

  await navigation(page).getByRole('link', { name: 'Lançamentos' }).click()
  const mercado = row(page, '🛒 Mercado')
  await expect(mercado).toContainText('−R$ 12,34')
  await expect(mercado).not.toContainText('Aguardando sinal', { timeout: SYNC_TIMEOUT_MS })
  await expect(mercado).toContainText('−R$ 12,34')
})

test('records the first expense in airplane mode (S1)', async ({ page, context }) => {
  const start = Date.now()
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  // The app shell is cached and the family's data has arrived (Task 11's warm-up).
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready
  })
  await expect(page.getByRole('radio', { name: '🛒 Mercado' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Hoje · Carteira · Eu' })).toBeVisible()

  await context.setOffline(true)
  await typeAmount(page, ['5', '0', '0'])
  await page.getByText('🚌 Transporte', { exact: true }).click()
  await page.getByRole('button', { name: 'Salvar' }).click()

  await expect(savedNotice(page)).toBeVisible()
  expect(Date.now() - start).toBeLessThan(S1_LIMIT_MS)
  await navigation(page).getByRole('link', { name: 'Lançamentos' }).click()
  const transporte = row(page, '🚌 Transporte')
  await expect(transporte).toContainText('−R$ 5,00')
  await expect(transporte).toContainText('Aguardando sinal')

  // Still offline, the app opens from the cache.
  await page.reload()
  await expect(page).toHaveURL('/lancamentos')
  await expect(transporte).toContainText('−R$ 5,00')
  await expect(transporte).toContainText('Aguardando sinal')
  await expect(page.getByRole('status')).toHaveText(
    'Sem conexão. Você pode lançar; os gastos saem quando o sinal voltar.',
  )

  // Back online, the queue sends the expense once.
  await context.setOffline(false)
  await expect(transporte).not.toContainText('Aguardando sinal', { timeout: SYNC_TIMEOUT_MS })
  await waitForSavedTransactions(page, 1)
  await page.reload()
  await expect(page.getByRole('listitem').filter({ hasText: '−R$ 5,00' })).toHaveCount(1)
})

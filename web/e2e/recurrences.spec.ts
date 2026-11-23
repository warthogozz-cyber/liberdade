import type { Page } from '@playwright/test'
import {
  addTagAndMethod,
  createFamily,
  createRule,
  expect,
  money,
  newUser,
  notices,
  recordExpense,
  signInWithGoogle,
  test,
  typeAmount,
  waitForQueueToEmpty,
} from './support.ts'

/** The same day one month later, as the API's `plusMonths` does it: the 31st lands on a short month's last day. */
function sameDayNextMonth(date: string): string {
  const [year = 0, month = 1, day = 1] = date.split('-').map(Number)
  const first = new Date(Date.UTC(year, month, 1))
  const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate()
  const nextDay = String(Math.min(day, lastDay)).padStart(2, '0')
  return `${first.toISOString().slice(0, 8)}${nextDay}`
}

/** A `YYYY-MM-DD` date as the Previstos rows write it: day and month. */
function shortDate(date: string): string {
  return `${date.slice(8, 10)}/${date.slice(5, 7)}`
}

async function createCorrente(page: Page): Promise<void> {
  await page.goto('/mais/contas')
  await page.getByRole('button', { name: 'Nova conta' }).click()
  const form = page.getByRole('dialog', { name: 'Nova conta' })
  await form.getByLabel('Nome', { exact: true }).fill('Corrente')
  await form.getByRole('radio', { name: 'Conta corrente', exact: true }).check()
  await typeAmount(form, '500000')
  await form.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText('Conta criada.')).toBeVisible()
}

async function expectBalance(page: Page, amount: string): Promise<void> {
  await page.goto('/mais/contas')
  await expect(page.getByRole('button', { name: /Corrente/ })).toContainText(money(amount))
}

function aluguel(page: Page) {
  return page.getByRole('region', { name: 'Previstos' }).getByRole('listitem')
}

test('a monthly rent shows as planned, then Confirmar makes it the day entry', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await createCorrente(page)

  const startOn = await createRule(page, {
    description: 'Aluguel',
    amount: '1500,00',
    account: 'Corrente',
    category: '🛒 Mercado',
  })
  await expect(
    page.getByRole('main').getByRole('listitem').filter({ hasText: 'Aluguel' }),
  ).toContainText(`Todo mês, dia ${Number(startOn.slice(8, 10))}`)

  // The planned one is on the list and does not touch the balance.
  await page.goto('/lancamentos')
  await expect(page.getByRole('heading', { level: 2, name: 'Previstos' })).toBeVisible()
  await expect(aluguel(page)).toHaveCount(1)
  await expect(aluguel(page).first()).toContainText('Aluguel')
  await expect(aluguel(page).first()).toContainText(money('1.500,00'))
  await expect(aluguel(page).first()).toContainText(shortDate(startOn))
  await expectBalance(page, '5.000,00')

  // Confirmar takes it out of the section and into the day.
  await page.goto('/lancamentos')
  await page.getByRole('button', { name: 'Confirmar Aluguel' }).click()
  await expect(notices(page).getByText('Previsto confirmado.')).toBeVisible()
  await expect(page.getByRole('heading', { level: 2, name: 'Previstos' })).toHaveCount(0)
  const day = page.getByRole('list', { name: 'Hoje' })
  await expect(day.getByRole('listitem').filter({ hasText: money('1.500,00') })).toContainText(
    'Corrente',
  )
  await expectBalance(page, '3.500,00')
})

test('the next month planned one is skipped and the skip is undone', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)

  const startOn = await createRule(page, {
    description: 'Aluguel',
    amount: '1500,00',
    category: '🛒 Mercado',
  })
  const next = sameDayNextMonth(startOn)

  await page.goto('/lancamentos')
  await page.getByRole('button', { name: 'Próximo mês' }).click()
  await expect(aluguel(page)).toHaveCount(1)
  await expect(aluguel(page).first()).toContainText(shortDate(next))

  await aluguel(page)
    .first()
    .getByRole('link', { name: /Aluguel/ })
    .click()
  const sheet = page.getByRole('dialog', { name: 'Previsto' })
  await sheet.getByRole('button', { name: 'Pular' }).click()
  await expect(notices(page).getByText('Previsto pulado')).toBeVisible()
  await expect(page.getByRole('heading', { level: 2, name: 'Previstos' })).toHaveCount(0)

  await notices(page).getByRole('button', { name: 'Desfazer' }).click()
  await expect(page.getByRole('heading', { level: 2, name: 'Previstos' })).toBeVisible()
  await expect(aluguel(page)).toHaveCount(1)
  await expect(aluguel(page).first()).toContainText(shortDate(next))
})

test('an expense paid with Pix and tagged Escola is the only one the tag filter shows', async ({
  page,
}) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)

  await page.goto('/lancar')
  await typeAmount(page, '8000')
  await page.getByText('🛒 Mercado', { exact: true }).click()
  await addTagAndMethod(page, 'Pix', 'Escola')
  await page.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText('Gasto salvo')).toBeVisible()
  await expect(page).toHaveURL('/inicio')

  await recordExpense(page, '2500', '🛒 Mercado')
  await waitForQueueToEmpty(page)
  const rows = page.getByRole('main').getByRole('listitem')
  await expect(rows.filter({ hasText: 'Pix · Escola' })).toHaveCount(1)
  await expect(rows.filter({ hasText: money('25,00') })).toHaveCount(1)

  await page.getByRole('button', { name: 'Filtros' }).click()
  await page.getByRole('group', { name: 'Tag' }).getByRole('radio', { name: 'Escola' }).click()
  await expect(page.getByRole('button', { name: 'Limpar Escola' })).toBeVisible()
  await expect(rows.filter({ hasText: 'Pix · Escola' })).toHaveCount(1)
  await expect(rows.filter({ hasText: money('25,00') })).toHaveCount(0)
})

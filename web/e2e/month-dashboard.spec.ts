import type { Page } from '@playwright/test'
import {
  createFamily,
  expect,
  money,
  newUser,
  notices,
  pickAccount,
  signInWithGoogle,
  test,
  typeAmount,
  waitForQueueToEmpty,
} from './support.ts'

async function createAccount(
  page: Page,
  name: string,
  type: string,
  balanceDigits: string,
): Promise<void> {
  await page.getByRole('button', { name: 'Nova conta' }).click()
  const form = page.getByRole('dialog', { name: 'Nova conta' })
  await form.getByLabel('Nome', { exact: true }).fill(name)
  await form.getByRole('radio', { name: type, exact: true }).check()
  if (balanceDigits) await typeAmount(form, balanceDigits)
  await form.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText('Conta criada.')).toBeVisible()
  await expect(page.getByRole('button', { name: new RegExp(name) })).toBeVisible()
}

async function save(page: Page, notice: string): Promise<void> {
  await page.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText(notice)).toBeVisible()
  await expect(page).toHaveURL('/inicio')
}

function card(page: Page, label: string) {
  return page.getByText(label, { exact: true }).locator('..')
}

test('the PRD example shows up on the month dashboard', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)

  await page.goto('/mais/contas')
  await createAccount(page, 'Corrente', 'Conta corrente', '100000')
  await createAccount(page, 'Poupança', 'Poupança', '')

  // Income of R$ 5.000,00 in Salário, on Corrente.
  await page.goto('/lancar?tipo=receita')
  await typeAmount(page, '500000')
  await page.getByText('💼 Salário', { exact: true }).click()
  await pickAccount(page, 'Corrente')
  await save(page, 'Receita salva')

  // Expense of R$ 1.234,56 in Mercado, on Corrente.
  await page.goto('/lancar')
  await typeAmount(page, '123456')
  await page.getByText('🛒 Mercado', { exact: true }).click()
  await pickAccount(page, 'Corrente')
  await save(page, 'Gasto salvo')

  // Transfer of R$ 500,00 from Corrente to Poupança.
  await page.goto('/lancar?tipo=transferencia')
  await typeAmount(page, '50000')
  await page.getByRole('group', { name: 'De' }).getByRole('radio', { name: 'Corrente' }).check()
  await page.getByRole('group', { name: 'Para' }).getByRole('radio', { name: 'Poupança' }).check()
  await save(page, 'Transferência salva')

  await waitForQueueToEmpty(page)
  await page.goto('/painel')

  await expect(card(page, 'Saldo total')).toContainText(money('4.765,44'))
  await expect(card(page, 'Receitas')).toContainText(money('5.000,00'))
  await expect(card(page, 'Gastos')).toContainText(money('1.234,56'))
  await expect(card(page, 'Resultado do mês')).toContainText(money('3.765,44'))

  await page.getByRole('group').filter({ hasText: 'Ver tabela' }).first().click()
  const donut = page.getByRole('table', { name: 'Gastos por categoria' })
  await expect(donut.getByRole('row', { name: /Mercado/ })).toContainText(money('1.234,56'))

  await page.goto('/mais/contas')
  await expect(page.getByRole('button', { name: /Corrente/ })).toContainText(money('4.265,44'))
  await expect(page.getByRole('button', { name: /Poupança/ })).toContainText(money('500,00'))

  // Tapping Mercado on the dashboard opens the list filtered to that category.
  await page.goto('/painel')
  await page
    .getByRole('list', { name: 'Gastos por categoria' })
    .getByRole('link', { name: /Mercado/ })
    .click()
  await expect(page).toHaveURL(/\/lancamentos\?.*categoria=/)
  const rows = page.getByRole('main').getByRole('listitem')
  await expect(rows).toHaveCount(1)
  await expect(rows.first()).toContainText('Mercado')
  await expect(rows.first()).toContainText(money('1.234,56'))
})

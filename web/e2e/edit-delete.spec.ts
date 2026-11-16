import { expect, test, type Page } from '@playwright/test'
import {
  createFamily,
  eraseAmount,
  money,
  newUser,
  notices,
  recordExpense,
  signInWithGoogle,
  typeAmount,
  waitForQueueToEmpty,
} from './support.ts'

/** A signed-in family with one synced expense of R$ 10,00 in Mercado, on the list. */
async function withTenReaisExpense(page: Page): Promise<void> {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await recordExpense(page, '1000', '🛒 Mercado')
  await waitForQueueToEmpty(page)
}

function mercado(page: Page) {
  return page.getByRole('main').getByRole('listitem').filter({ hasText: 'Mercado' })
}

async function openEdit(page: Page): Promise<void> {
  await page.goto('/lancamentos')
  await mercado(page).getByRole('button').click()
  await page.getByRole('link', { name: 'Editar' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Editar lançamento' })).toBeVisible()
}

async function changeAmountTo(page: Page, digits: string): Promise<void> {
  await expect(page.getByRole('status', { name: 'Valor' })).toContainText(money('10,00'))
  await eraseAmount(page, 4)
  await typeAmount(page, digits)
}

test('editsAmount: a new amount shows on the list and on Início', async ({ page }) => {
  await withTenReaisExpense(page)
  await openEdit(page)
  await changeAmountTo(page, '1200')
  await page.getByRole('button', { name: 'Salvar' }).click()

  await expect(notices(page).getByText('Lançamento atualizado.')).toBeVisible()
  await expect(page).toHaveURL(/\/lancamentos/)
  await expect(mercado(page)).toContainText(money('12,00'))

  await page.goto('/inicio')
  await expect(page.getByRole('main')).toContainText(/Gastos em \p{L}+\s*R\$\s12,00/u)
})

test('deleteAndUndo: the expense goes away and Desfazer brings it back', async ({ page }) => {
  await withTenReaisExpense(page)
  await page.goto('/lancamentos')
  await mercado(page).getByRole('button').click()
  await page.getByRole('button', { name: 'Excluir' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Excluir' }).click()

  await expect(notices(page).getByText('Lançamento excluído.')).toBeVisible()
  await expect(mercado(page)).toHaveCount(0)

  await notices(page).getByRole('button', { name: 'Desfazer' }).click()
  await expect(notices(page).getByText('Lançamento restaurado.')).toBeVisible()
  await expect(mercado(page)).toContainText(money('10,00'))
})

test('conflictBetweenTabs: the second save is told the expense changed', async ({
  page,
  context,
}) => {
  await withTenReaisExpense(page)
  const second = await context.newPage()
  await openEdit(page)
  await openEdit(second)

  await changeAmountTo(page, '2000')
  await page.getByRole('button', { name: 'Salvar' }).click()
  await expect(page).toHaveURL(/\/lancamentos/)
  await expect(mercado(page)).toContainText(money('20,00'))

  await changeAmountTo(second, '3000')
  await second.getByRole('button', { name: 'Salvar' }).click()
  await expect(second.getByRole('alertdialog', { name: 'Este lançamento mudou' })).toBeVisible()
  await second.getByRole('button', { name: 'Salvar a minha mesmo assim' }).click()

  await expect(second).toHaveURL(/\/lancamentos/)
  await expect(mercado(second)).toContainText(money('30,00'))
})

test('offlineDisablesEditing: editing waits for the signal, recording still works', async ({
  page,
  context,
}) => {
  await withTenReaisExpense(page)
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready
  })
  await page.goto('/lancamentos')
  await expect(mercado(page)).toBeVisible()

  await context.setOffline(true)
  await expect(page.getByRole('status')).toContainText(
    'Sem conexão. Lançar funciona; editar volta com o sinal.',
  )
  await mercado(page).getByRole('button').click()
  await expect(page.getByRole('button', { name: 'Editar' })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Excluir' })).toBeDisabled()
  await page.keyboard.press('Escape')

  // Recording is still allowed offline: the expense is queued for the signal.
  await page.goto('/lancar')
  await typeAmount(page, '500')
  await page.getByText('🚌 Transporte', { exact: true }).click()
  await page.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText('Gasto salvo')).toBeVisible()

  await context.setOffline(false)
  await waitForQueueToEmpty(page)
  await expect(
    page.getByRole('main').getByRole('listitem').filter({ hasText: 'Transporte' }),
  ).toContainText(money('5,00'))
})

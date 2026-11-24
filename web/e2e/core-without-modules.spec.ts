import type { Page } from '@playwright/test'
import {
  createFamily,
  expect,
  money,
  newUser,
  notices,
  recordExpense,
  setModules,
  signInWithGoogle,
  test,
  typeAmount,
  waitForQueueToEmpty,
} from './support.ts'

/** The app has no error boundary of its own, so these are the copies a failed screen would show. */
async function expectNoErrorScreen(page: Page): Promise<void> {
  await expect(page.getByText('Unexpected Application Error!')).toHaveCount(0)
  await expect(page.getByText('Não deu para carregar', { exact: false })).toHaveCount(0)
  await expect(page.getByText('Não encontramos o que você procurou.')).toHaveCount(0)
}

async function visit(page: Page, path: string, heading: string): Promise<void> {
  await page.goto(path)
  await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
  await expectNoErrorScreen(page)
}

function mercado(page: Page) {
  return page.getByRole('main').getByRole('listitem').filter({ hasText: 'Mercado' })
}

test('coreWorksWithEveryModuleOff: the core is whole when no module is on (S2)', async ({
  page,
}) => {
  // Nothing is allow-listed: any console error or 5xx during the whole test fails it.
  const consoleErrors: string[] = []
  const serverErrors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  page.on('response', (response) => {
    if (response.status() >= 500) serverErrors.push(`${response.status()} ${response.url()}`)
  })

  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await setModules(page, false)

  // The planning module is off: the bar has no Planejar, and Mais has no Recorrências.
  await visit(page, '/mais', 'Mais')
  await expect(page.getByRole('link', { name: 'Planejar' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Recorrências' })).toHaveCount(0)
  // The bar has its four items, Início and Mais among them, and the home has no planning cards.
  const bar = page.getByRole('navigation', { name: 'Navegação principal' })
  await expect(bar.getByRole('listitem')).toHaveCount(4)
  await page.goto('/inicio')
  await expect(page.getByText('Saldo total')).toBeVisible()
  // An address typed by hand does not open the planning hub either: it lands on the home.
  await page.goto('/planejar')
  await expect(page).toHaveURL('/inicio')
  await expect(page.getByRole('heading', { level: 1, name: 'Planejar' })).toHaveCount(0)
  await expect(page.getByText('Saldo total')).toBeVisible()
  for (const name of [
    'Orçamento do mês',
    'Reserva de emergência',
    'Saldo previsto · estimativa',
    'Gastos do mês',
  ]) {
    await expect(page.getByRole('heading', { level: 2, name })).toHaveCount(0)
  }

  await visit(page, '/mais/modulos', 'Módulos')
  for (const name of ['Planejamento', 'Indicadores', 'Conquistas', 'Projetos']) {
    await expect(page.getByRole('switch', { name })).toHaveAttribute('aria-checked', 'false')
  }

  // An account and a category.
  await visit(page, '/mais/contas', 'Contas')
  await page.getByRole('button', { name: 'Nova conta' }).click()
  const account = page.getByRole('dialog', { name: 'Nova conta' })
  await account.getByLabel('Nome', { exact: true }).fill('Corrente')
  await account.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText('Conta criada.')).toBeVisible()
  await expect(page.getByRole('button', { name: /Corrente/ })).toBeVisible()

  await visit(page, '/mais/categorias', 'Categorias')
  await page.getByRole('button', { name: 'Nova categoria' }).click()
  const category = page.getByRole('dialog', { name: 'Nova categoria' })
  await category.getByLabel('Nome', { exact: true }).fill('Hobbies')
  await category.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText('Categoria criada.')).toBeVisible()

  // An expense, an income and a transfer.
  await recordExpense(page, '1000', '🛒 Mercado')

  await page.goto('/lancar?tipo=receita')
  await typeAmount(page, '500000')
  await page.getByText('💼 Salário', { exact: true }).click()
  await page.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText('Receita salva')).toBeVisible()
  await expect(page).toHaveURL('/inicio')

  await page.goto('/lancar?tipo=transferencia')
  await typeAmount(page, '2500')
  await page.getByRole('group', { name: 'De' }).getByRole('radio', { name: 'Carteira' }).check()
  await page.getByRole('group', { name: 'Para' }).getByRole('radio', { name: 'Corrente' }).check()
  await page.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText('Transferência salva')).toBeVisible()
  await expect(page).toHaveURL('/inicio')

  // The list, then edit and delete with Desfazer.
  await waitForQueueToEmpty(page)
  await expectNoErrorScreen(page)
  await expect(page.getByRole('heading', { level: 2, name: 'Previstos' })).toHaveCount(0)
  await expect(mercado(page)).toContainText(money('10,00'))
  await mercado(page).getByRole('button').click()
  await page.getByRole('link', { name: 'Editar' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Editar lançamento' })).toBeVisible()
  await expect(page.getByRole('status', { name: 'Valor' })).toContainText(money('10,00'))
  for (let i = 0; i < 4; i += 1) await page.getByRole('button', { name: 'Apagar' }).click()
  await typeAmount(page, '1200')
  await page.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText('Lançamento atualizado.')).toBeVisible()
  await expect(mercado(page)).toContainText(money('12,00'))

  await mercado(page).getByRole('button').click()
  await page.getByRole('button', { name: 'Excluir' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: 'Excluir' }).click()
  await expect(notices(page).getByText('Lançamento excluído.')).toBeVisible()
  await expect(mercado(page)).toHaveCount(0)
  await notices(page).getByRole('button', { name: 'Desfazer' }).click()
  await expect(notices(page).getByText('Lançamento restaurado.')).toBeVisible()
  await expect(mercado(page)).toContainText(money('12,00'))

  await visit(page, '/painel', 'Painel do mês')
  await visit(page, '/mais/familia', 'Família')
  await visit(page, '/mais/privacidade', 'Privacidade')
  await visit(page, '/mais/privacidade/atividade', 'Histórico de acessos')
  await expect(page.getByRole('main').getByRole('listitem').first()).toBeVisible()

  expect(consoleErrors).toEqual([])
  expect(serverErrors).toEqual([])
})

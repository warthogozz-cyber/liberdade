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
  waitForOfflineReady,
  waitForQueueToEmpty,
} from './support.ts'

const DISCLAIMER = 'Conteúdo educativo. Não é recomendação de investimento.'
const SIMULATOR_NOTE =
  'Simulação com taxa constante, sem imposto de renda nem taxas. Não é promessa de rendimento.'
const TEMPLATE_NAME = 'Doces por encomenda'

/** A new family with every module on, signed in. */
async function signInWithModules(page: Page): Promise<void> {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await setModules(page, true)
}

/** The way a person gets there: Mais, Projetos, Começar, the Culinária track, then create. */
async function createFoodVenture(page: Page): Promise<void> {
  await page.goto('/mais')
  await page.getByRole('link', { name: 'Projetos', exact: true }).click()
  await expect(page).toHaveURL('/mais/projetos')
  await expect(page.getByRole('heading', { level: 1, name: 'Projetos' })).toBeVisible()
  await page.getByRole('link', { name: 'Começar um projeto' }).click()
  await expect(page).toHaveURL('/mais/projetos/comecar')
  await page.getByRole('button', { name: /^Culinária/ }).click()

  const sheet = page.getByRole('dialog', { name: 'Começar com o modelo' })
  await expect(sheet.getByLabel('Nome do projeto')).toHaveValue(TEMPLATE_NAME)
  await sheet.getByRole('button', { name: 'Criar projeto' }).click()
  await expect(page).toHaveURL(/\/mais\/projetos\/[^/]+\/editar$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Editar projeto' })).toBeVisible()
}

test('createFromTemplateAndSeeThePrd: the model fills the form with example stamps and the preview', async ({
  page,
}) => {
  await signInWithModules(page)
  await createFoodVenture(page)

  // The three initial items, the four fixed costs and the product all come from the model.
  await expect(page.getByText('exemplo: ajuste para a sua realidade')).toHaveCount(8)
  await expect(page.getByText(/Os itens com o selo “exemplo” vêm do modelo/)).toBeVisible()
  await expect(page.getByLabel('Nome do projeto')).toHaveValue(TEMPLATE_NAME)

  const preview = page.getByRole('region', { name: 'Prévia das contas' })
  await expect(preview.getByText('2,70')).toBeVisible()
  await expect(preview).toContainText('60,0%')
  await expect(preview).toContainText('100 unidades')
  await expect(preview).toContainText(money('450,00'))
  await expect(preview).toContainText(money('540,00'))
  await expect(preview).toContainText('2 meses')
  await expect(preview).toContainText('710,0%')
  await expect(
    preview.getByText('Com os números que você digitou. Não é promessa de resultado.'),
  ).toBeVisible()
})

test('editingUpdatesThePreview: a new price changes the preview before saving, and the screen after', async ({
  page,
}) => {
  await signInWithModules(page)
  await createFoodVenture(page)

  const preview = page.getByRole('region', { name: 'Prévia das contas' })
  await expect(preview).toContainText(money('540,00'))

  await page.getByLabel('Preço de venda (R$)').fill('5,00')
  // The preview waits a moment after the last change, then asks the API: nothing is saved yet.
  await expect(preview).toContainText('3,20')
  await expect(preview).not.toContainText('2,70')
  await expect(page).toHaveURL(/\/editar$/)

  await page.getByRole('button', { name: 'Salvar', exact: true }).click()
  await expect(page).toHaveURL(/\/mais\/projetos\/[^/]+$/)
  await expect(page.getByRole('heading', { level: 1, name: TEMPLATE_NAME })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Prévia das contas' })).toContainText('3,20')
  await expect(page.getByRole('region', { name: 'Produtos' })).toContainText(money('5,00'))
})

test('stepsAndStatus: tick a step, start selling, close: the project moves to Encerrados', async ({
  page,
}) => {
  await signInWithModules(page)
  await createFoodVenture(page)
  await page.getByRole('button', { name: 'Salvar', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name: TEMPLATE_NAME })).toBeVisible()

  const step = page.getByRole('checkbox', {
    name: 'Teste a receita e anote o custo de cada unidade',
  })
  await expect(step).not.toBeChecked()
  await step.click()
  await expect(step).toBeChecked()

  await page.getByRole('button', { name: 'Mudar o status' }).click()
  const sheet = page.getByRole('dialog', { name: 'Status do projeto' })
  await sheet.getByRole('button', { name: 'Começar a vender', exact: true }).click()
  await sheet.getByRole('button', { name: 'Confirmar' }).click()
  await expect(sheet).toBeHidden()
  await expect(page.getByText('Em andamento', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Mudar o status' }).click()
  await sheet.getByRole('button', { name: 'Encerrar', exact: true }).click()
  await sheet.getByRole('button', { name: 'Confirmar' }).click()
  await expect(sheet).toBeHidden()
  await expect(page.getByText('Encerrado', { exact: true })).toBeVisible()

  await page.goto('/mais/projetos')
  const closed = page.getByText('Encerrados (1)')
  await expect(closed).toBeVisible()
  // The group starts folded: its project shows once the person opens it.
  await closed.click()
  await expect(page.getByRole('link', { name: new RegExp(TEMPLATE_NAME) })).toBeVisible()
})

test('investmentsTrack: the texts and the simulator both carry the notice, and a scenario simulates', async ({
  page,
}) => {
  await signInWithModules(page)
  // Right after the modules change, a fresh load can still read the old modules from the cache
  // and bounce: load again until the start screen holds with its tracks.
  await expect(async () => {
    await page.goto('/mais/projetos/comecar')
    await expect(page.getByRole('link', { name: /^Investimentos/ })).toBeVisible({ timeout: 3_000 })
    await expect(page).toHaveURL(/\/mais\/projetos\/comecar$/)
  }).toPass({ timeout: 45_000 })
  await page.getByRole('link', { name: /^Investimentos/ }).click()
  await expect(page).toHaveURL('/mais/projetos/investimentos')
  await expect(page.getByRole('heading', { level: 1, name: 'Investimentos' })).toBeVisible()
  await expect(page.getByText(DISCLAIMER)).toBeVisible()

  await page.getByRole('link', { name: 'Simular juros compostos' }).click()
  await expect(page).toHaveURL('/mais/projetos/investimentos/simulador')
  await expect(
    page.getByRole('heading', { level: 1, name: 'Simulador de juros compostos' }),
  ).toBeVisible()
  await expect(page.getByText(DISCLAIMER)).toBeVisible()

  await page.getByLabel('Valor inicial (R$)').fill('1000')
  await page.getByLabel('Aporte mensal (R$)').fill('100')
  await page.getByLabel('Prazo (anos, de 1 a 50)').fill('5')
  // The first row may come filled with an indicator or empty: either way, the person's own values win.
  await page.getByLabel('Nome do cenário 1').fill('Meu cenário')
  await page.getByLabel('Taxa ao ano (%) do cenário 1').fill('10')
  await page.getByRole('button', { name: 'Simular', exact: true }).click()

  await expect(page.getByRole('heading', { level: 2, name: 'Resultado' })).toBeVisible()
  const result = page.getByRole('region', { name: 'Meu cenário' })
  await expect(result).toContainText('Saldo final')
  await expect(result).toContainText('Total aportado')
  await expect(page.getByText(SIMULATOR_NOTE)).toBeVisible()
})

/** A project in progress, as its page leaves it: the model's project, saved, then "Começar a vender". */
async function startFoodVenture(page: Page): Promise<string> {
  await createFoodVenture(page)
  await page.getByRole('button', { name: 'Salvar', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name: TEMPLATE_NAME })).toBeVisible()
  await page.getByRole('button', { name: 'Mudar o status' }).click()
  const sheet = page.getByRole('dialog', { name: 'Status do projeto' })
  await sheet.getByRole('button', { name: 'Começar a vender', exact: true }).click()
  await sheet.getByRole('button', { name: 'Confirmar' }).click()
  await expect(sheet).toBeHidden()
  await expect(page.getByText('Em andamento', { exact: true })).toBeVisible()
  const id = /\/mais\/projetos\/([^/]+)$/.exec(page.url())?.[1]
  expect(id, 'the project id in the URL').toBeTruthy()
  return id!
}

/** Opens the form for a kind ("gasto" or "receita") with the project field ready. */
async function openMarkableForm(page: Page, kind: 'gasto' | 'receita'): Promise<void> {
  await expect(async () => {
    await page.goto(kind === 'receita' ? '/lancar?tipo=receita' : '/lancar')
    await expect(page.getByLabel('Projeto (opcional)')).toBeVisible({ timeout: 3_000 })
  }).toPass({ timeout: 45_000 })
}

/** Records a transaction marked with the project: the digits are cents, the category its label. */
async function recordMarked(
  page: Page,
  kind: 'gasto' | 'receita',
  digits: string,
  category: string,
): Promise<void> {
  await openMarkableForm(page, kind)
  await typeAmount(page, digits)
  await page.getByText(category, { exact: true }).click()
  await page.getByLabel('Projeto (opcional)').selectOption({ label: TEMPLATE_NAME })
  await page.getByRole('button', { name: 'Salvar' }).click()
  await expect(
    notices(page).getByText(kind === 'gasto' ? 'Gasto salvo' : 'Receita salva'),
  ).toBeVisible()
  await expect(page).toHaveURL('/inicio')
}

test('markATransactionAndSeeTheActual: an expense and an income marked with the project show in the real versus planned', async ({
  page,
}) => {
  await signInWithModules(page)
  await startFoodVenture(page)
  const venturePage = page.url()

  await recordMarked(page, 'gasto', '80000', '🛒 Mercado')
  await recordMarked(page, 'receita', '30000', '💼 Salário')
  // Saved on the device first: the real only counts them once they reached the server.
  await waitForQueueToEmpty(page)

  await page.goto(venturePage)
  await page.getByRole('link', { name: 'Real vs. planejado ›' }).click()
  await expect(page).toHaveURL(/\/mais\/projetos\/[^/]+\/real$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Real vs. planejado' })).toBeVisible()

  const real = page.getByRole('region', { name: 'Real', exact: true })
  await expect(real).toContainText(money('500,00'))
  await expect(real).toContainText(/ainda não se pagou: faltam\s+R\$\s500,00/)

  // Both land in the same month: one row, with the income and the expense.
  const month = page.getByRole('row', { name: /^\p{L}+\/\d{4}/u })
  await expect(month).toHaveCount(1)
  await expect(month).toContainText(money('300,00'))
  await expect(month).toContainText(money('800,00'))
})

test('offlineMarkedTransactionSyncs: an income marked with the project while offline syncs and shows in the real', async ({
  page,
  context,
}) => {
  await signInWithModules(page)
  const id = await startFoodVenture(page)
  await waitForOfflineReady(page)
  // The projects are kept on the device: load the form online, then lose the network.
  await openMarkableForm(page, 'receita')

  await context.setOffline(true)
  await typeAmount(page, '30000')
  await page.getByText('💼 Salário', { exact: true }).click()
  await page.getByLabel('Projeto (opcional)').selectOption({ label: TEMPLATE_NAME })
  await page.getByRole('button', { name: 'Salvar' }).click()
  await expect(notices(page).getByText('Receita salva')).toBeVisible()

  await page
    .getByRole('navigation', { name: 'Navegação principal' })
    .getByRole('link', { name: 'Lançamentos' })
    .click()
  const income = page.getByRole('listitem').filter({ hasText: '💼 Salário' })
  await expect(income).toContainText('Aguardando sinal')

  // Back online, the queue sends it with its project.
  await context.setOffline(false)
  await expect(income).not.toContainText('Aguardando sinal', { timeout: 20_000 })

  await page.goto(`/mais/projetos/${id}/real`)
  await expect(page.getByRole('heading', { level: 1, name: 'Real vs. planejado' })).toBeVisible()
  const month = page.getByRole('row', { name: /^\p{L}+\/\d{4}/u })
  await expect(month).toHaveCount(1)
  await expect(month).toContainText(money('300,00'))
})

test('filterFromTheProject: "Ver lançamentos do projeto" lists only the marked ones', async ({
  page,
}) => {
  await signInWithModules(page)
  const id = await startFoodVenture(page)

  await recordMarked(page, 'gasto', '1200', '🛒 Mercado')
  await recordExpense(page, '700', '🚌 Transporte')
  await waitForQueueToEmpty(page)

  await page.goto(`/mais/projetos/${id}/real`)
  await page.getByRole('link', { name: 'Ver lançamentos do projeto' }).click()
  // The link opens the project's newest month.
  await expect(page).toHaveURL(new RegExp(`/lancamentos\\?projeto=${id}&mes=\\d{4}-\\d{2}$`))
  await expect(page.getByRole('listitem').filter({ hasText: '🛒 Mercado' })).toBeVisible()
  await expect(page.getByRole('listitem').filter({ hasText: '🚌 Transporte' })).toHaveCount(0)
})

import { fileURLToPath } from 'node:url'
import AxeBuilder from '@axe-core/playwright'
import type { Locator, Page } from '@playwright/test'
import {
  acceptDocuments,
  addTagAndMethod,
  addVirtualAuthenticator,
  buyFamilyPlan,
  completeGoogleSignIn,
  createFamily,
  createInvitation,
  createRule,
  expect,
  joinAsYouth,
  money,
  newUser,
  openInvitation,
  familyMonth,
  recordExpense,
  seedBudgetLimit,
  seedGoal,
  setModules,
  signInBudget,
  signInWithGoogle,
  stubFirstReauthRequired,
  test,
  typeAmount,
  waitForQueueToEmpty,
  withContext,
} from './support.ts'

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

/**
 * Checks the page against WCAG 2.2 AA in the light and dark themes, once its `<h1>` and the content
 * that `ready` points at are on the screen.
 */
async function expectNoViolations(page: Page, heading: string, ready: Locator): Promise<void> {
  await expectNoViolationsWith(page, ready, () =>
    expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible(),
  )
}

/** The same check with a sheet or dialog open, which hides the page's own heading from the tree. */
async function expectNoViolationsOpen(page: Page, ready: Locator): Promise<void> {
  await expectNoViolationsWith(page, ready, async () => {})
}

async function expectNoViolationsWith(
  page: Page,
  ready: Locator,
  ensureHeading: () => Promise<void>,
): Promise<void> {
  for (const colorScheme of ['light', 'dark'] as const) {
    // Without motion, a sheet is fully opaque at once: axe would read colors mid-fade-in.
    await page.emulateMedia({ colorScheme, reducedMotion: 'reduce' })
    await ensureHeading()
    await expect(ready).toBeVisible()
    const results = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()
    expect(results.violations, `${page.url()} (${colorScheme})`).toEqual([])
  }
}

test('/entrar meets WCAG 2.2 AA in both themes', async ({ page }) => {
  await page.goto('/entrar')

  await expectNoViolations(
    page,
    'Liberdade',
    page.getByRole('button', { name: 'Entrar com Google' }),
  )
})

test('the public legal pages meet WCAG 2.2 AA in both themes', async ({ page }) => {
  await page.goto('/privacidade')
  await expectNoViolations(
    page,
    'Política de privacidade',
    page.getByRole('heading', { level: 2, name: 'Quem cuida dos seus dados?' }),
  )

  await page.goto('/termos')
  await expectNoViolations(
    page,
    'Termos de uso',
    page.getByRole('heading', { level: 2, name: 'O que é o Liberdade?' }),
  )
})

test('the acceptance gate and the passkey list meet WCAG 2.2 AA in both themes', async ({
  page,
}) => {
  test.setTimeout(180_000)
  await addVirtualAuthenticator(page)

  // A new person stops at "Antes de começar", so the sign-in stops short of `signInWithGoogle`.
  await signInBudget.reserve(1)
  await page.goto('/entrar')
  await page.getByRole('button', { name: 'Entrar com Google' }).click()
  await completeGoogleSignIn(page, newUser())
  await expectNoViolations(
    page,
    'Antes de começar',
    page.getByRole('checkbox', { name: /Li e aceito os termos/ }),
  )
  await acceptDocuments(page)
  await page.waitForURL('**/boas-vindas')
  await createFamily(page)

  await page.goto('/mais')
  await page.getByRole('button', { name: 'Criar passkey neste aparelho' }).click()
  await expect(page.getByRole('status')).toHaveText('Passkey criada.')

  await page.goto('/mais/privacidade')
  const row = page.getByRole('main').getByRole('listitem').filter({ hasText: 'Criada em' })
  await expectNoViolations(page, 'Privacidade', row)
  await row.getByRole('button', { name: /^Remover passkey/ }).click()
  await expectNoViolationsOpen(page, page.getByRole('dialog', { name: /^Remover a passkey/ }))
})

test('/boas-vindas meets WCAG 2.2 AA in both themes', async ({ page }) => {
  await signInWithGoogle(page, newUser())

  await expectNoViolations(page, 'Olá, Ana! 👋', page.getByRole('button', { name: 'Começar' }))
})

test('the family screens meet WCAG 2.2 AA in both themes', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  const screens: { path: string; heading: string; ready: Locator }[] = [
    {
      path: '/lancar',
      heading: 'Novo gasto',
      ready: page.getByRole('radio', { name: '🛒 Mercado' }),
    },
    { path: '/inicio', heading: 'Início', ready: page.getByText('Saldo total') },
    {
      path: '/lancamentos',
      heading: 'Lançamentos',
      ready: page.getByText('Nenhum lançamento neste mês.'),
    },
    { path: '/mais', heading: 'Mais', ready: page.getByRole('button', { name: 'Sair' }) },
    {
      path: '/mais/ajustes',
      heading: 'Ajustes',
      ready: page.getByRole('switch', { name: 'Sons de conquista' }),
    },
  ]

  for (const { path, heading, ready } of screens) {
    await page.goto(path)
    await expectNoViolations(page, heading, ready)
  }
})

test('the screens to view and organize meet WCAG 2.2 AA in both themes', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await recordExpense(page, '1234', '🛒 Mercado')
  await waitForQueueToEmpty(page)

  await expectNoViolations(
    page,
    'Lançamentos',
    page.getByRole('main').getByRole('listitem').filter({ hasText: 'Mercado' }),
  )
  await page.getByRole('main').getByRole('listitem').filter({ hasText: 'Mercado' }).click()
  await expectNoViolationsOpen(page, page.getByRole('link', { name: 'Editar' }))
  await page.keyboard.press('Escape')

  await page.goto('/mais/contas')
  await page.getByRole('button', { name: 'Nova conta' }).click()
  await expectNoViolationsOpen(page, page.getByRole('dialog', { name: 'Nova conta' }))
  await page.keyboard.press('Escape')

  await page.goto('/mais/categorias')
  await page.getByRole('button', { name: 'Nova categoria' }).click()
  await expectNoViolationsOpen(page, page.getByRole('dialog', { name: 'Nova categoria' }))
  await page.keyboard.press('Escape')

  await page.goto('/painel')
  await page.getByRole('group').filter({ hasText: 'Ver tabela' }).first().click()
  await expectNoViolations(
    page,
    'Painel do mês',
    page.getByRole('table', { name: 'Gastos por categoria' }),
  )

  // A transfer needs two accounts: the family starts with one.
  await page.goto('/mais/contas')
  await page.getByRole('button', { name: 'Nova conta' }).click()
  await page
    .getByRole('dialog', { name: 'Nova conta' })
    .getByLabel('Nome', { exact: true })
    .fill('Corrente')
  await page
    .getByRole('dialog', { name: 'Nova conta' })
    .getByRole('button', { name: 'Salvar' })
    .click()
  await expect(page.getByRole('button', { name: /Corrente/ })).toBeVisible()

  await page.goto('/lancar?tipo=transferencia')
  await expectNoViolations(page, 'Nova transferência', page.getByRole('group', { name: 'Para' }))
})

test('the family, privacy and module screens meet WCAG 2.2 AA in both themes', async ({
  browser,
  page,
}) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)

  // A member to open, and an invitation that has not been used, for the anonymous page.
  await withContext(browser, async (_context, youth) => {
    const link = await createInvitation(page, 'Leo', 'Jovem')
    await joinAsYouth(youth, link, 'Leo')

    await page.goto('/mais/familia')
    await expectNoViolations(
      page,
      'Família',
      page.getByRole('main').getByRole('listitem').filter({ hasText: 'Leo' }),
    )
    await page
      .getByRole('main')
      .getByRole('listitem')
      .filter({ hasText: 'Leo' })
      .getByRole('button')
      .click()
    await expectNoViolationsOpen(page, page.getByRole('dialog', { name: 'Leo' }))
    await page.keyboard.press('Escape')

    // The invite screen with the link made, and the page that link opens, signed out.
    const open = await createInvitation(page, 'Gil', 'Adulto')
    await expectNoViolations(page, 'Convidar alguém', page.getByLabel('Link do convite'))
    await withContext(browser, async (_anonymous, guest) => {
      await openInvitation(guest, open, /convidou você para a .+ como Adulto/)
      await expectNoViolations(
        guest,
        'convidou você',
        guest.getByRole('button', { name: 'Entrar com Google' }),
      )
    })

    await page.goto('/mais/modulos')
    await expectNoViolations(page, 'Módulos', page.getByRole('switch', { name: 'Planejamento' }))

    await page.goto('/mais/privacidade/atividade')
    await expectNoViolations(
      page,
      'Histórico de acessos',
      page.getByRole('main').getByRole('listitem').first(),
    )

    await page.goto('/mais/privacidade')
    await expectNoViolations(
      page,
      'Privacidade',
      page.getByRole('button', { name: 'Baixar meus dados' }),
    )
    await page.getByRole('button', { name: 'Excluir minha conta' }).click()
    await expectNoViolationsOpen(page, page.getByRole('dialog', { name: 'Excluir minha conta' }))

    // The sign-in again dialog, asked for by the API's own answer (the sign-in is seconds old).
    await stubFirstReauthRequired(page, /\/v1\/me\/deletion$/, 'POST')
    await page
      .getByRole('dialog', { name: 'Excluir minha conta' })
      .getByRole('button', { name: 'Excluir minha conta' })
      .click()
    await expectNoViolationsOpen(page, page.getByRole('dialog', { name: 'Confirme que é você' }))
    await page.keyboard.press('Escape')

    // Leo's account waits to be deleted: the pending screen replaces his app.
    await youth.goto('/mais/privacidade')
    await youth.getByRole('button', { name: 'Excluir minha conta' }).click()
    await youth
      .getByRole('dialog', { name: 'Excluir minha conta' })
      .getByRole('button', { name: 'Excluir minha conta' })
      .click()
    await expectNoViolations(
      youth,
      'Sua conta será excluída em',
      youth.getByRole('button', { name: 'Cancelar exclusão' }),
    )
  })
})

test('the Plan and Portrait screens meet WCAG 2.2 AA in both themes', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await recordExpense(page, '1234', '🛒 Mercado')
  await waitForQueueToEmpty(page)

  await page.goto('/mais/plano')
  await expectNoViolations(
    page,
    'Plano',
    page.getByRole('button', { name: 'Ser Família Fundadora' }),
  )

  await page.goto('/painel/retrato')
  await expectNoViolations(page, 'Retrato do mês', page.getByRole('img', { name: /^Retrato de / }))
})

test('Recorrências, Previstos and the Tags tab meet WCAG 2.2 AA in both themes', async ({
  page,
}) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)

  // A tagged expense gives the Tags tab a tag to list.
  await page.goto('/lancar')
  await typeAmount(page, '1234')
  await page.getByText('🛒 Mercado', { exact: true }).click()
  await addTagAndMethod(page, 'Pix', 'Escola')
  await expectNoViolationsOpen(page, page.getByRole('combobox', { name: 'Tags' }))
  await page.getByRole('button', { name: 'Salvar' }).click()
  await expect(page).toHaveURL('/inicio')

  await createRule(page, { description: 'Aluguel', amount: '1500,00', category: '🛒 Mercado' })
  await expectNoViolations(
    page,
    'Recorrências',
    page.getByRole('main').getByRole('listitem').filter({ hasText: 'Aluguel' }),
  )
  await page.getByRole('button', { name: 'Nova recorrência' }).click()
  await expectNoViolationsOpen(page, page.getByRole('dialog', { name: 'Nova recorrência' }))
  await page.keyboard.press('Escape')

  await page.goto('/lancamentos')
  await expectNoViolations(
    page,
    'Lançamentos',
    page.getByRole('region', { name: 'Previstos' }).getByRole('listitem').first(),
  )

  await page.goto('/mais/categorias')
  await page.getByRole('tab', { name: 'Tags' }).click()
  await expectNoViolations(
    page,
    'Categorias',
    page.getByRole('main').getByRole('listitem').filter({ hasText: 'Escola' }),
  )
  await page.getByRole('button', { name: 'Renomear Escola' }).click()
  await expectNoViolationsOpen(page, page.getByRole('dialog', { name: 'Renomear Escola' }))
})

test('the indicators screens and the robot page meet WCAG 2.2 AA in both themes', async ({
  page,
}) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await page.goto('/mais/modulos')
  await page.getByRole('switch', { name: 'Indicadores' }).click()
  await expect(page.getByRole('switch', { name: 'Indicadores' })).toHaveAttribute(
    'aria-checked',
    'true',
  )

  // Reached through Mais, as a person does: a reload right after the switch could still read the
  // persisted module list from before it. The startup collection runs a few seconds after the API
  // is up, so the data may take a moment.
  await page.goto('/mais')
  await page.getByRole('link', { name: 'Indicadores' }).click()
  await expectNoViolations(
    page,
    'Indicadores',
    page.getByRole('main').getByText('15,00% a.a.').first(),
  )

  await page.getByRole('link', { name: 'Calcular rendimento real' }).click()
  await expectNoViolations(
    page,
    'Rendimento real',
    page.getByRole('main').getByText('Fonte:', { exact: false }).first(),
  )
  await page.getByLabel('Rendimento ao ano (%)').fill('10')
  await expect(page.getByRole('main').getByText('5,77%', { exact: true })).toBeVisible()
  await expectNoViolations(
    page,
    'Rendimento real',
    page.getByRole('main').getByText('5,77%', { exact: true }),
  )

  await page.goto('/robo')
  await expectNoViolations(
    page,
    'Robô de indicadores do Liberdade',
    page.getByRole('heading', { level: 1 }),
  )
})

test('the planning screens and the home cards meet WCAG 2.2 AA in both themes', async ({
  page,
}) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  // A limit, an expense inside it and a goal, so the screens and cards have content to check.
  await seedBudgetLimit(page, await familyMonth(page), 'Mercado', '100.00')
  await seedGoal(page, 'Viagem', '1000.00')
  await recordExpense(page, '8500', '🛒 Mercado')
  await waitForQueueToEmpty(page)

  await page.goto('/planejar')
  await expectNoViolations(page, 'Planejar', page.getByRole('link', { name: /^Orçamento/ }))

  await page.goto('/planejar/orcamento')
  await expectNoViolations(page, 'Orçamento', page.getByRole('button', { name: /Mercado/ }))
  await page.getByRole('button', { name: 'Definir limite' }).click()
  await expectNoViolationsOpen(page, page.getByRole('dialog', { name: 'Definir limite' }))
  await page.keyboard.press('Escape')

  await page.goto('/planejar/metas')
  await expectNoViolations(page, 'Metas', page.getByRole('meter', { name: 'Viagem' }))
  await page.getByRole('button', { name: /Viagem/ }).click()
  await expectNoViolationsOpen(page, page.getByRole('button', { name: 'Guardar', exact: true }))
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: 'Nova meta' }).click()
  await expectNoViolationsOpen(page, page.getByRole('dialog', { name: 'Nova meta' }))
  await page.keyboard.press('Escape')

  await page.goto('/planejar/reserva')
  await expectNoViolations(
    page,
    'Reserva',
    page.getByText('Quanto a família gasta por mês, mais ou menos?'),
  )

  // Início with every card of the planning module.
  await page.goto('/inicio')
  await expectNoViolations(
    page,
    'Início',
    page.getByRole('heading', { level: 2, name: 'Saldo previsto · estimativa' }),
  )
  await expect(page.getByRole('heading', { level: 2, name: 'Orçamento do mês' })).toBeVisible()
  await expect(page.getByRole('heading', { level: 2, name: 'Reserva de emergência' })).toBeVisible()
  await expect(page.getByRole('heading', { level: 2, name: 'Gastos do mês' })).toBeVisible()
})

test('the statement import meets WCAG 2.2 AA in both themes', async ({ page, request }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await buyFamilyPlan(page, request)

  await page.goto('/lancamentos/importar')
  const file = page.getByLabel('Arquivo do extrato')
  await file.setInputFiles(
    fileURLToPath(new URL('../src/features/import/fixtures/ofx-sgml-1252.ofx', import.meta.url)),
  )
  await expectNoViolations(
    page,
    'Importar extrato',
    page.getByRole('list', { name: 'Confira os lançamentos' }),
  )

  // A CSV whose header says nothing known asks which column is which.
  await file.setInputFiles({
    name: 'extrato.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from('Quando;O que;Quanto;Outro\n03/11/2026;LOJA A;-45,90;1,00\n'),
  })
  await expectNoViolations(
    page,
    'Importar extrato',
    page.getByRole('heading', { level: 2, name: 'Quais colunas são quais?' }),
  )
})

test('Conquistas and the streak card meet WCAG 2.2 AA in both themes', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await setModules(page, true)
  await recordExpense(page, '1234', '🛒 Mercado')
  await waitForQueueToEmpty(page)

  await page.goto('/inicio')
  await expectNoViolations(
    page,
    'Início',
    page.getByRole('heading', { level: 2, name: 'Sequência' }),
  )

  // One earned and the rest still ahead, so both lists have content.
  await page.goto('/mais/conquistas')
  await expectNoViolations(
    page,
    'Conquistas',
    page.getByRole('heading', { level: 2, name: 'Ganhas' }),
  )
  await expect(page.getByRole('heading', { level: 2, name: 'Próximas' })).toBeVisible()
})

test.describe('with the tips on', () => {
  test.use({ tips: true })

  test('an open tip and an open tour meet WCAG 2.2 AA in both themes', async ({ page }) => {
    await signInWithGoogle(page, newUser())
    await createFamily(page)
    await setModules(page, true)

    await page.goto('/lancamentos')
    await expectNoViolations(
      page,
      'Lançamentos',
      page.getByText('Todos os lançamentos do mês. Toque em um para editar ou excluir.'),
    )

    await page.goto('/mais/conquistas')
    await expectNoViolations(
      page,
      'Conquistas',
      page.getByRole('dialog', { name: /Sua sequência conta as semanas/ }),
    )
  })
})

test('the projects screens meet WCAG 2.2 AA in both themes', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await setModules(page, true)

  // From the model, so the form and the project screen have example stamps, figures and steps.
  await page.goto('/mais/projetos/comecar')
  await expectNoViolations(
    page,
    'Começar um projeto',
    page.getByRole('button', { name: /^Culinária/ }),
  )
  await page.getByRole('button', { name: /^Culinária/ }).click()
  await page
    .getByRole('dialog', { name: 'Começar com o modelo' })
    .getByRole('button', { name: 'Criar projeto' })
    .click()
  await expect(page).toHaveURL(/\/mais\/projetos\/[^/]+\/editar$/)

  const figures = page.getByRole('region', { name: 'Prévia das contas' })
  await expectNoViolations(page, 'Editar projeto', figures.getByText(money('540,00')))

  await page.getByRole('button', { name: 'Salvar' }).click()
  await expectNoViolations(
    page,
    'Doces por encomenda',
    page.getByRole('heading', { level: 2, name: 'Passos' }),
  )

  await page.goto('/mais/projetos')
  await expectNoViolations(
    page,
    'Projetos',
    page.getByRole('link', { name: /Doces por encomenda/ }),
  )

  await page.goto('/mais/projetos/investimentos')
  await expectNoViolations(
    page,
    'Investimentos',
    page.getByText('Conteúdo educativo. Não é recomendação de investimento.'),
  )

  await page.goto('/mais/projetos/investimentos/simulador')
  await expectNoViolations(
    page,
    'Simulador de juros compostos',
    page.getByRole('button', { name: 'Simular', exact: true }),
  )
})

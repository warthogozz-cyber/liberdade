import AxeBuilder from '@axe-core/playwright'
import type { Locator, Page } from '@playwright/test'
import {
  acceptDocuments,
  addVirtualAuthenticator,
  completeGoogleSignIn,
  createFamily,
  createInvitation,
  expect,
  joinAsYouth,
  newUser,
  openInvitation,
  recordExpense,
  signInBudget,
  signInWithGoogle,
  stubFirstReauthRequired,
  test,
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
    await page.emulateMedia({ colorScheme })
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

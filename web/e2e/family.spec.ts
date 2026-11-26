import type { Browser, BrowserContext, Page } from '@playwright/test'
import {
  completeGoogleSignIn,
  CONTEXT_OPTIONS,
  createFamily,
  createInvitation,
  expect,
  invitationBudget,
  joinAsYouth,
  money,
  newUser,
  notices,
  openInvitation,
  signInBudget,
  signInWithGoogle,
  test,
  typeAmount,
  waitForQueueToEmpty,
  withContext,
} from './support.ts'
import { WEB_URL } from './urls.ts'

const AUTH_FILE = 'test-results/.auth/family.json'

// Each test needs a signed-in Dona, who is not what it tests, so one sign-in per file is reused
// through `storageState`. Every test below invites someone with a name of its own.
let owner: BrowserContext

test.beforeAll(async ({ browser }) => {
  await withContext(browser, async (context, page) => {
    await signInWithGoogle(page, newUser('Ana'))
    await createFamily(page, 'Ana')
    await context.storageState({ path: AUTH_FILE })
  })
})

test.beforeEach(async ({ browser }) => {
  owner = await browser.newContext({ ...CONTEXT_OPTIONS, storageState: AUTH_FILE })
})

test.afterEach(async () => {
  await owner.close()
})

async function ownerPage(): Promise<Page> {
  return owner.newPage()
}

/** Invites a Youth and has them join in a second context; returns that context for the test to use. */
async function youthJoins(browser: Browser, page: Page, name: string) {
  const link = await createInvitation(page, name, 'Jovem')
  const context = await browser.newContext(CONTEXT_OPTIONS)
  try {
    const youth = await context.newPage()
    await joinAsYouth(youth, link, name)
    return { context, youth, link }
  } catch (error) {
    await context.close()
    throw error
  }
}

test('adultJoinsWithGoogle: Bia joins as an Adult and the Dona sees her in Família', async ({
  browser,
}) => {
  const dona = await ownerPage()
  const link = await createInvitation(dona, 'Bia', 'Adulto')

  await withContext(browser, async (_context, bia) => {
    await openInvitation(bia, link, /convidou você para a .+ como Adulto/)
    await invitationBudget.reserve(1)
    await signInBudget.reserve(1)
    await bia.getByRole('button', { name: 'Entrar com Google' }).click()
    await completeGoogleSignIn(bia, newUser('Bia'))

    // Back from Google, the same link's page offers to join, signed in.
    await invitationBudget.reserve(1)
    await bia.getByRole('button', { name: 'Entrar na família' }).click()
    await expect(bia).toHaveURL('/inicio')
    await expect(bia.getByRole('heading', { level: 1, name: 'Início' })).toBeVisible()
  })

  await dona.goto('/mais/familia')
  await expect(
    dona.getByRole('main').getByRole('listitem').filter({ hasText: 'Bia' }),
  ).toContainText('Adulto')
})

test('youthJoinsWithPasskey: Leo gets only his wallet, spends, and the Dona sees who', async ({
  browser,
}) => {
  const dona = await ownerPage()
  const { context, youth } = await youthJoins(browser, dona, 'Leo')
  try {
    // Only Carteira de Leo: the Dona's wallet is not his to see.
    await youth.goto('/lancar')
    await youth.getByRole('button', { name: /^(Hoje|Ontem|\d+ de \p{L}+\.?) · .+ · Eu/u }).click()
    await expect(youth.getByRole('radio', { name: 'Carteira de Leo', exact: true })).toBeChecked()
    await expect(youth.getByRole('group', { name: 'Conta' }).getByRole('radio')).toHaveCount(1)
    await youth.keyboard.press('Escape')

    // The Dona moves R$ 50,00 to the wallet.
    await dona.goto('/lancar?tipo=transferencia')
    await typeAmount(dona, '5000')
    await dona
      .getByRole('group', { name: 'De' })
      .getByRole('radio', { name: 'Carteira', exact: true })
      .check()
    await dona
      .getByRole('group', { name: 'Para' })
      .getByRole('radio', { name: 'Carteira de Leo', exact: true })
      .check()
    await dona.getByRole('button', { name: 'Salvar' }).click()
    await expect(notices(dona).getByText('Transferência salva')).toBeVisible()
    await waitForQueueToEmpty(dona)

    // Leo spends R$ 10,00 and has R$ 40,00 left.
    await youth.goto('/lancar')
    await typeAmount(youth, '1000')
    await youth.getByText('🛒 Mercado', { exact: true }).click()
    await youth.getByRole('button', { name: 'Salvar' }).click()
    await expect(notices(youth).getByText('Gasto salvo')).toBeVisible()
    await waitForQueueToEmpty(youth)
    await youth.goto('/inicio')
    await expect(youth.getByText('Sua carteira', { exact: true }).locator('..')).toContainText(
      money('40,00'),
    )

    // The Dona sees the expense credited to Leo.
    await dona.goto('/lancamentos')
    const row = dona.getByRole('main').getByRole('listitem').filter({ hasText: 'Mercado' })
    // "Carteira de Leo" holds the name too, so the member is read where it stands alone.
    await expect(row).toContainText('Carteira de Leo · Leo')
    await expect(row).toContainText(money('10,00'))
    await row.getByRole('button').click()
    await expect(
      dona
        .getByRole('dialog')
        .locator('dt', { hasText: 'Lançado por' })
        .locator('xpath=following-sibling::dd'),
    ).toHaveText('Leo')
  } finally {
    await context.close()
  }
})

test('inviteUsedTwice: the link of an accepted invitation no longer works', async ({ browser }) => {
  const dona = await ownerPage()
  const { context, link } = await youthJoins(browser, dona, 'Cris')
  try {
    await withContext(browser, async (_third, third) => {
      await invitationBudget.reserve(1)
      await third.goto(link)
      await expect(third.getByText('Este convite não vale mais')).toBeVisible()
    })
  } finally {
    await context.close()
  }
})

test('tokenNotInHistory: opening the link leaves no token in the address', async ({ browser }) => {
  const dona = await ownerPage()
  const link = await createInvitation(dona, 'Dani', 'Adulto')
  const token = new URL(link).hash.slice(1)
  expect(token).not.toBe('')

  await withContext(browser, async (_context, guest) => {
    await openInvitation(guest, link, /convidou você para a .+ como Adulto/)
    expect(guest.url()).not.toContain(token)
    expect(guest.url()).toBe(`${WEB_URL}/convite`)
    expect(await guest.evaluate(() => window.location.hash)).toBe('')
  })
})

test('signedInAccountOnYouthInvite: a signed-in Google account is told to sign out first', async ({
  browser,
}) => {
  const dona = await ownerPage()
  const link = await createInvitation(dona, 'Duda', 'Jovem')

  await withContext(browser, async (_context, edu) => {
    // Edu has an account of his own, with Google, and no family yet.
    await signInWithGoogle(edu, newUser('Edu'))
    await openInvitation(edu, link, /como Jovem/)
    await invitationBudget.reserve(1)
    await edu.getByRole('button', { name: 'Entrar na família' }).click()

    // The API refuses (a Youth comes in only with a new passkey) and the page says so.
    await expect(
      edu.getByText(
        'Convites de Jovem só podem ser aceitos com uma passkey nova. Saia da sua conta para continuar.',
      ),
    ).toBeVisible()
    await expect(edu.getByRole('button', { name: 'Sair e continuar' })).toBeVisible()
    await expect(edu.getByRole('button', { name: 'Entrar na família' })).toHaveCount(0)
  })
})

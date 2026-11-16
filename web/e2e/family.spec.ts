import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test'
import {
  completeGoogleSignIn,
  createFamily,
  createInvitation,
  invitationBudget,
  joinAsYouth,
  money,
  newUser,
  notices,
  openInvitation,
  signInBudget,
  signInWithGoogle,
  typeAmount,
  waitForQueueToEmpty,
  withContext,
} from './support.ts'

const AUTH_FILE = 'test-results/.auth/family.json'

// Each test needs a signed-in Dona, who is not what it tests, so one sign-in per file is reused
// through `storageState`. Every test below invites someone with a name of its own.
let owner: BrowserContext

test.beforeAll(async ({ browser }) => {
  const context = await browser.newContext({ locale: 'pt-BR', timezoneId: 'America/Fortaleza' })
  const page = await context.newPage()
  await signInWithGoogle(page, newUser('Ana'))
  await createFamily(page, 'Ana')
  await context.storageState({ path: AUTH_FILE })
  await context.close()
})

test.beforeEach(async ({ browser }) => {
  owner = await browser.newContext({
    locale: 'pt-BR',
    timezoneId: 'America/Fortaleza',
    storageState: AUTH_FILE,
  })
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
  const context = await browser.newContext({ locale: 'pt-BR', timezoneId: 'America/Fortaleza' })
  const youth = await context.newPage()
  await joinAsYouth(youth, link, name)
  return { context, youth, link }
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
    await expect(row).toContainText('Leo')
    await expect(row).toContainText(money('10,00'))
    await row.getByRole('button').click()
    await expect(dona.getByRole('dialog')).toContainText('Leo')
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
    expect(guest.url()).toBe('http://localhost:5173/convite')
    expect(await guest.evaluate(() => window.location.hash)).toBe('')
  })
})

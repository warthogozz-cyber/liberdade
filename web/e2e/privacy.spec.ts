import { expect, test, type BrowserContext } from '@playwright/test'
import {
  completeGoogleSignIn,
  createFamily,
  createInvitation,
  invitationBudget,
  newUser,
  openInvitation,
  signInBudget,
  signInWithGoogle,
  stubFirstReauthRequired,
  withContext,
  CONTEXT_OPTIONS,
} from './support.ts'

const AUTH_FILE = 'test-results/.auth/privacy.json'

// The Dona is not the subject of most tests here, so one sign-in per file is reused through
// `storageState`. The sign-out test signs in on its own, since it ends its session.
let owner: BrowserContext

test.beforeAll(async ({ browser }) => {
  const context = await browser.newContext(CONTEXT_OPTIONS)
  const page = await context.newPage()
  await signInWithGoogle(page, newUser('Ana'))
  await createFamily(page, 'Ana')
  await context.storageState({ path: AUTH_FILE })
  await context.close()
})

test.beforeEach(async ({ browser }) => {
  owner = await browser.newContext({ ...CONTEXT_OPTIONS, storageState: AUTH_FILE })
})

test.afterEach(async () => {
  await owner.close()
})

test("exportMyData: Baixar meus dados downloads the person's zip", async () => {
  const page = await owner.newPage()
  await page.goto('/mais/privacidade')
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Baixar meus dados' }).click()

  expect((await download).suggestedFilename()).toMatch(/^liberdade-meus-dados-\d{4}-\d{2}-\d{2}\.zip$/)
})

test('deleteAndCancel: an Adult asks to delete the account, confirms with Google and cancels', async ({
  browser,
}) => {
  // The public invitation limit is 10 a minute for the whole suite, and this test spends three of
  // them after the family specs spent theirs, so it may wait out a window (see `support.ts`).
  test.setTimeout(180_000)
  const eva = newUser('Eva')
  const link = await createInvitation(await owner.newPage(), 'Eva', 'Adulto')

  await withContext(browser, async (_context, page) => {
    await openInvitation(page, link, /como Adulto/)
    await invitationBudget.reserve(1)
    await signInBudget.reserve(1)
    await page.getByRole('button', { name: 'Entrar com Google' }).click()
    await completeGoogleSignIn(page, eva)
    await invitationBudget.reserve(1)
    await page.getByRole('button', { name: 'Entrar na família' }).click()
    await expect(page).toHaveURL('/inicio')

    // The API asks for a sign-in from the last 10 minutes, and this one is seconds old. So the first
    // request is answered with the API's own refusal, to bring the dialog up; the one after the
    // confirmation is real. The API side is covered by AccountDeletionIT.deletionNeedsRecentSignIn.
    await stubFirstReauthRequired(page, /\/v1\/me\/deletion$/, 'POST')
    await page.goto('/mais/privacidade')
    await page.getByRole('button', { name: 'Excluir minha conta' }).click()
    const sheet = page.getByRole('dialog', { name: 'Excluir minha conta' })
    await sheet.getByRole('button', { name: 'Excluir minha conta' }).click()

    const reauth = page.getByRole('dialog', { name: 'Confirme que é você' })
    await expect(reauth).toBeVisible()
    await signInBudget.reserve(1)
    await reauth.getByRole('button', { name: 'Entrar com Google' }).click()
    await completeGoogleSignIn(page, eva)

    // Back where she was, she asks again, and this time the API accepts.
    await expect(page).toHaveURL('/mais/privacidade')
    await page.getByRole('button', { name: 'Excluir minha conta' }).click()
    await page
      .getByRole('dialog', { name: 'Excluir minha conta' })
      .getByRole('button', { name: 'Excluir minha conta' })
      .click()
    await expect(
      page.getByRole('heading', { level: 1, name: /Sua conta será excluída em/ }),
    ).toBeVisible()

    await page.getByRole('button', { name: 'Cancelar exclusão' }).click()
    await expect(page).toHaveURL('/inicio')
    await expect(page.getByRole('heading', { level: 1, name: 'Início' })).toBeVisible()
  })
})

test('soleOwnerBlocked: the only Dona is told to hand the family over first', async () => {
  const page = await owner.newPage()
  await page.goto('/mais/privacidade')
  await page.getByRole('button', { name: 'Excluir minha conta' }).click()
  await page
    .getByRole('dialog', { name: 'Excluir minha conta' })
    .getByRole('button', { name: 'Excluir minha conta' })
    .click()

  await expect(page.getByRole('alert')).toContainText('Você é o único Dono de uma família.')
})

test('signOutPropagates: signing out in one tab sends the other to /entrar', async ({
  browser,
}) => {
  await withContext(browser, async (context, first) => {
    await signInWithGoogle(first, newUser('Ana'))
    await createFamily(first, 'Ana')
    const second = await context.newPage()
    await second.goto('/inicio')
    await expect(second.getByRole('heading', { level: 1, name: 'Início' })).toBeVisible()

    await first.goto('/mais')
    await first.getByRole('button', { name: 'Sair' }).click()

    await expect(second).toHaveURL(/\/entrar/)
    await expect(first).toHaveURL(/\/entrar/)
  })
})

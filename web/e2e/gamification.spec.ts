import type { BrowserContext, Page } from '@playwright/test'
import {
  completeGoogleSignIn,
  createFamily,
  createInvitation,
  expect,
  invitationBudget,
  newUser,
  openInvitation,
  recordExpense,
  setModules,
  signInBudget,
  signInWithGoogle,
  test,
  waitForQueueToEmpty,
  withContext,
} from './support.ts'

const FIRST_TRANSACTION = 'Primeiro lançamento'

/** An Adult joins the Dona's family in their own context and lands on Início. */
async function adultJoins(context: BrowserContext, donaPage: Page): Promise<Page> {
  const link = await createInvitation(donaPage, 'Bia', 'Adulto')
  const bia = await context.newPage()
  await openInvitation(bia, link, /convidou você para a .+ como Adulto/)
  await invitationBudget.reserve(1)
  await signInBudget.reserve(1)
  await bia.getByRole('button', { name: 'Entrar com Google' }).click()
  await completeGoogleSignIn(bia, newUser('Bia'))
  await invitationBudget.reserve(1)
  await bia.getByRole('button', { name: 'Entrar na família' }).click()
  await expect(bia).toHaveURL('/inicio')
  return bia
}

/** The list on Conquistas under a heading ("Ganhas" or "Próximas"). */
function achievementsUnder(page: Page, heading: 'Ganhas' | 'Próximas') {
  return page
    .locator('section')
    .filter({ has: page.getByRole('heading', { level: 2, name: heading }) })
}

test('streak and achievements for two people', async ({ browser, page }) => {
  test.setTimeout(240_000)
  await signInWithGoogle(page, newUser('Ana'))
  await createFamily(page, 'Ana')
  // The modules start off. A fresh load of the next screen reads them on.
  await setModules(page, true)

  // The Dona's own expense celebrates and starts her streak.
  await recordExpense(page, '1234', '🛒 Mercado')
  await expect(page.getByText(`Conquista: ${FIRST_TRANSACTION}`)).toBeVisible({ timeout: 20_000 })
  await expect(page.getByText('1 semana seguida')).toBeVisible({ timeout: 20_000 })

  await withContext(browser, async (biaContext) => {
    const bia = await adultJoins(biaContext, page)

    // The Adult has no streak yet, and the achievement is still ahead.
    await expect(bia.getByText('Lance algo esta semana para começar uma sequência.')).toBeVisible({
      timeout: 20_000,
    })
    await bia.goto('/mais/conquistas')
    await expect(bia.getByRole('heading', { level: 1, name: 'Conquistas' })).toBeVisible()
    await expect(
      achievementsUnder(bia, 'Próximas').getByText(FIRST_TRANSACTION, { exact: true }),
    ).toBeVisible()
    await expect(achievementsUnder(bia, 'Ganhas')).toHaveCount(0)

    // The Adult's own expense celebrates, and the achievement moves to "Ganhas".
    await recordExpense(bia, '500', '🛒 Mercado')
    await expect(bia.getByText(`Conquista: ${FIRST_TRANSACTION}`)).toBeVisible({ timeout: 20_000 })
    await waitForQueueToEmpty(bia)
    await bia.goto('/mais/conquistas')
    await expect(
      achievementsUnder(bia, 'Ganhas').getByText(FIRST_TRANSACTION, { exact: true }),
    ).toBeVisible()
    await expect(
      achievementsUnder(bia, 'Próximas').getByText(FIRST_TRANSACTION, { exact: true }),
    ).toHaveCount(0)
    await bia.goto('/inicio')
    await expect(bia.getByText('1 semana seguida')).toBeVisible({ timeout: 20_000 })
  })
})

test('turning the module on does not celebrate', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  // The expense comes first, with the modules off: it can only be caught up later, silently.
  await recordExpense(page, '1234', '🛒 Mercado')
  await waitForQueueToEmpty(page)
  await setModules(page, true)

  const earned = achievementsUnder(page, 'Ganhas').getByText(FIRST_TRANSACTION, { exact: true })
  await expect(async () => {
    await page.goto('/mais/conquistas')
    await expect(page.getByRole('heading', { level: 1, name: 'Conquistas' })).toBeVisible()
    await expect(earned).toBeVisible({ timeout: 3_000 })
  }).toPass({ timeout: 30_000 })
  await expect(page.getByText(/^Conquista: /)).toHaveCount(0)
})

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
  openInvitation,
  recordExpense,
  signInBudget,
  signInWithGoogle,
  test,
  withContext,
} from './support.ts'

/** The notice has to reach the other browser well before the next time anyone would reload. */
const LIVE_TIMEOUT_MS = 5_000

function entries(page: Page) {
  return page.getByRole('main').getByRole('listitem')
}

/** Bia joins the Dona's family as an Adult, in her own context, and opens the entries list. */
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

/**
 * The Dona records an expense and the call returns once the API has it: the app saves through a
 * queue, so the toast can come before the request, and the notice is timed from the response.
 */
async function recordExpenseSaved(page: Page, digits: string, category: string): Promise<void> {
  const saved = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      /\/v1\/workspaces\/[^/]+\/transactions$/.test(response.url()),
  )
  await recordExpense(page, digits, category)
  expect((await saved).ok()).toBe(true)
}

async function openEntries(page: Page): Promise<void> {
  await page.goto('/lancamentos')
  await expect(page.getByRole('heading', { level: 1, name: 'Lançamentos' })).toBeVisible()
}

async function signedInDona(browser: Browser, body: (dona: Page) => Promise<void>) {
  await withContext(browser, async (_context, dona) => {
    await signInWithGoogle(dona, newUser('Ana'))
    await createFamily(dona, 'Ana')
    await body(dona)
  })
}

test('entryAppearsInOtherBrowser: the Adult sees the Dona’s new entry without reloading', async ({
  browser,
}) => {
  test.setTimeout(180_000)
  await signedInDona(browser, async (dona) => {
    await withContext(browser, async (biaContext) => {
      const bia = await adultJoins(biaContext, dona)
      await openEntries(bia)
      await openEntries(dona)
      await expect(entries(bia).filter({ hasText: money('12,34') })).toHaveCount(0)

      await recordExpenseSaved(dona, '1234', '🛒 Mercado')

      // No reload and no navigation on Bia's side: only the live notice can bring the row.
      await expect(entries(bia).filter({ hasText: money('12,34') })).toBeVisible({
        timeout: LIVE_TIMEOUT_MS,
      })
    })
  })
})

test('youthDoesNotSeeAdultOnlyEntry: a Youth never gets an entry from an account that is not theirs', async ({
  browser,
}) => {
  test.setTimeout(180_000)
  await signedInDona(browser, async (dona) => {
    await withContext(browser, async (biaContext) => {
      const bia = await adultJoins(biaContext, dona)
      const link = await createInvitation(dona, 'Leo', 'Jovem')
      const youthContext = await browser.newContext(CONTEXT_OPTIONS)
      try {
        const leo = await youthContext.newPage()
        await joinAsYouth(leo, link, 'Leo')
        await openEntries(leo)
        await openEntries(bia)

        await recordExpenseSaved(dona, '4321', '🛒 Mercado')

        // The Adult's browser proves the notice went out; the Youth's list stays as it was.
        await expect(entries(bia).filter({ hasText: money('43,21') })).toBeVisible({
          timeout: LIVE_TIMEOUT_MS,
        })
        await expect(entries(leo).filter({ hasText: money('43,21') })).toHaveCount(0)
      } finally {
        await youthContext.close()
      }
    })
  })
})

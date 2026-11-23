import {
  completeGoogleSignIn,
  createFamily,
  createInvitation,
  expect,
  invitationBudget,
  newUser,
  openInvitation,
  signInBudget,
  signInWithGoogle,
  test,
  withContext,
} from './support.ts'

const GATE = { level: 1, name: 'Antes de começar' } as const
const DOCUMENTS_LIST = '**/v1/me/documents{,?*}'

test('legalPagesArePublic: the policy and the terms open without a session, from the sign-in screen too', async ({
  page,
}) => {
  await page.goto('/privacidade')
  await expect(
    page.getByRole('heading', { level: 1, name: 'Política de privacidade' }),
  ).toBeVisible()
  await expect(page.getByText(/Versão privacy-\d{4}-\d{2} · atualizada em /)).toBeVisible()
  await expect(
    page.getByRole('heading', { level: 2, name: 'Quem cuida dos seus dados?' }),
  ).toBeVisible()

  await page.goto('/termos')
  await expect(page.getByRole('heading', { level: 1, name: 'Termos de uso' })).toBeVisible()
  await expect(page.getByText(/Versão terms-\d{4}-\d{2} · atualizada em /)).toBeVisible()
  await expect(page.getByRole('heading', { level: 2, name: 'O que é o Liberdade?' })).toBeVisible()

  // From the sign-in screen, both links lead there.
  await page.goto('/entrar')
  const links = page.getByRole('navigation', { name: 'Documentos legais' })
  await links.getByRole('link', { name: 'Termos de uso' }).click()
  await expect(page).toHaveURL('/termos')
  await expect(page.getByRole('heading', { level: 1, name: 'Termos de uso' })).toBeVisible()

  await page.goto('/entrar')
  await links.getByRole('link', { name: 'Política de privacidade' }).click()
  await expect(page).toHaveURL('/privacidade')
  await expect(
    page.getByRole('heading', { level: 1, name: 'Política de privacidade' }),
  ).toBeVisible()
})

test('newPersonAcceptsBeforeUsing: a new person accepts once, then signs in again without the gate', async ({
  page,
}) => {
  test.setTimeout(180_000)
  const ana = newUser('Ana')
  await signInBudget.reserve(1)
  await page.goto('/entrar')
  await page.getByRole('button', { name: 'Entrar com Google' }).click()
  await completeGoogleSignIn(page, ana)

  // The gate comes before any screen of the family, and the button waits for the check.
  await expect(page.getByRole('heading', GATE)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Continuar' })).toBeDisabled()
  await page.getByRole('checkbox', { name: /Li e aceito os termos/ }).check()
  await page.getByRole('button', { name: 'Continuar' }).click()

  await page.waitForURL('**/boas-vindas')
  await createFamily(page, 'Ana')

  await page.goto('/mais')
  await page.getByRole('button', { name: 'Sair' }).click()
  await expect(page).toHaveURL(/\/entrar/)

  // The same person again: the accepted versions are still the ones in force.
  await signInBudget.reserve(1)
  await page.getByRole('button', { name: 'Entrar com Google' }).click()
  await completeGoogleSignIn(page, ana)
  await expect(page).toHaveURL(/\/(inicio|lancar)/)
  await expect(page.getByRole('heading', GATE)).toHaveCount(0)
})

test('inviteAcceptsDocuments: a guest joins by invitation and lands on Início without the gate', async ({
  browser,
  page,
}) => {
  test.setTimeout(180_000)
  await signInWithGoogle(page, newUser('Ana'))
  await createFamily(page, 'Ana')
  const link = await createInvitation(page, 'Bia', 'Adulto')

  await withContext(browser, async (_context, bia) => {
    await openInvitation(bia, link, /convidou você para a .+ como Adulto/)
    await invitationBudget.reserve(1)
    await signInBudget.reserve(1)
    await bia.getByRole('button', { name: 'Entrar com Google' }).click()
    await completeGoogleSignIn(bia, newUser('Bia'))

    // Back from Google, the invitation page offers to join: the gate does not stand in between.
    await expect(bia.getByRole('button', { name: 'Entrar na família' })).toBeVisible()
    await expect(bia.getByRole('heading', GATE)).toHaveCount(0)
    await invitationBudget.reserve(1)
    await bia.getByRole('button', { name: 'Entrar na família' }).click()

    await expect(bia).toHaveURL('/inicio')
    await expect(bia.getByRole('heading', { level: 1, name: 'Início' })).toBeVisible()
    await expect(bia.getByRole('heading', GATE)).toHaveCount(0)
  })
})

test('newVersionAsksAgain: whoever accepted the texts from before billing accepts the new ones', async ({
  page,
}) => {
  test.setTimeout(180_000)
  await signInWithGoogle(page, newUser('Ana'))
  await createFamily(page, 'Ana')

  // The e2e runs with billing on, so the texts in force are the ones with the Founder presale.
  await page.goto('/termos')
  await expect(page.getByText(/Versão terms-2026-12 · atualizada em /)).toBeVisible()
  await expect(
    page.getByRole('heading', { level: 2, name: 'O que é a Família Fundadora?' }),
  ).toBeVisible()

  // The API answers as if this person had accepted only the 2026-11 texts. The glob matches the list
  // with or without a query string, never `/accept`.
  await page.route(DOCUMENTS_LIST, async (route) => {
    const response = await route.fetch()
    const documents = (await response.json()) as { purpose: string; acceptedVersion: string }[]
    const older = documents.map((document) =>
      document.purpose === 'TERMS'
        ? { ...document, acceptedVersion: 'terms-2026-11' }
        : document.purpose === 'PRIVACY'
          ? { ...document, acceptedVersion: 'privacy-2026-11' }
          : document,
    )
    await route.fulfill({ response, json: older })
  })
  await page.goto('/inicio')
  await expect(page.getByRole('heading', GATE)).toBeVisible()

  // The accept carries the versions the API listed, and then the real answer lifts the gate.
  await page.unroute(DOCUMENTS_LIST)
  await page.getByRole('checkbox', { name: /Li e aceito os termos/ }).check()
  const accepted = page.waitForRequest(
    (request) => request.method() === 'POST' && request.url().endsWith('/v1/me/documents/accept'),
  )
  await page.getByRole('button', { name: 'Continuar' }).click()
  expect((await accepted).postDataJSON()).toEqual({
    terms: 'terms-2026-12',
    privacy: 'privacy-2026-12',
  })
  await expect(page.getByRole('heading', GATE)).toHaveCount(0)
  await expect(page.getByRole('heading', { level: 1, name: 'Início' })).toBeVisible()
})

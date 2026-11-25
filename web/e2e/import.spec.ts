import { fileURLToPath } from 'node:url'
import type { Page } from '@playwright/test'
import {
  buyFamilyPlan,
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

const STATEMENT = fileURLToPath(
  new URL('../src/features/import/fixtures/ofx-sgml-1252.ofx', import.meta.url),
)

/** The preview line whose checkbox is named like `name`. */
function line(page: Page, name: RegExp, index = 0) {
  return page
    .getByRole('listitem')
    .filter({ has: page.getByRole('checkbox', { name }) })
    .nth(index)
}

test('an adult imports a statement once', async ({ page, request, browser }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await buyFamilyPlan(page, request)
  const link = await createInvitation(page, 'Bia', 'Adulto')

  await withContext(browser, async (_context, bia) => {
    await openInvitation(bia, link, /convidou você para a .+ como Adulto/)
    await invitationBudget.reserve(1)
    await signInBudget.reserve(1)
    await bia.getByRole('button', { name: 'Entrar com Google' }).click()
    await completeGoogleSignIn(bia, newUser('Bia'))
    await invitationBudget.reserve(1)
    await bia.getByRole('button', { name: 'Entrar na família' }).click()
    await expect(bia).toHaveURL('/inicio')

    await bia.goto('/lancamentos')
    await bia.getByRole('link', { name: 'Importar extrato' }).click()
    await expect(bia).toHaveURL('/lancamentos/importar')
    await expect(bia.getByRole('heading', { level: 1, name: 'Importar extrato' })).toBeVisible()
    await bia.getByLabel('Arquivo do extrato').setInputFiles(STATEMENT)

    const lines = bia.getByRole('list', { name: 'Confira os lançamentos' })
    await expect(lines.getByRole('checkbox')).toHaveCount(4)
    await line(bia, /PADARIA PAO QUENTE/)
      .getByText('🛒 Mercado', { exact: true })
      .click()
    await line(bia, /PADARIA PAO QUENTE/)
      .getByRole('button', { name: 'Usar a mesma categoria nas 2 sem categoria' })
      .click()
    await line(bia, /Doação mensal/)
      .getByText('🎁 Presentes', { exact: true })
      .click()
    await bia.getByRole('button', { name: 'Importar 4 lançamentos' }).click()
    await expect(bia.getByText(/4 entraram/)).toBeVisible()

    // The same file again: every line is already in, so nothing is checked.
    await bia.getByRole('button', { name: 'Importar outro arquivo' }).click()
    await bia.getByLabel('Arquivo do extrato').setInputFiles(STATEMENT)
    await expect(lines.getByRole('checkbox')).toHaveCount(4)
    await expect(lines.getByText('Já importado', { exact: true })).toHaveCount(4)
    for (const box of await lines.getByRole('checkbox').all()) await expect(box).not.toBeChecked()
    await expect(bia.getByRole('button', { name: 'Importar 0 lançamentos' })).toBeDisabled()
  })
})

import { expect, test } from './support.ts'

test('sends a visitor without a session to the sign-in screen', async ({ page }) => {
  await page.goto('/')

  await expect(page).toHaveURL('/entrar')
  await expect(page.getByRole('button', { name: 'Entrar com Google' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Entrar com passkey' })).toBeVisible()
})

test('brings a sign-in refused at Google back to the sign-in screen (Review Focus 3)', async ({
  page,
}) => {
  await page.goto('http://localhost:8080/login/oauth2/code/google?error=access_denied&state=x')

  await expect(page).toHaveURL('http://localhost:5173/entrar?erro=login')
  await expect(page.getByRole('alert')).toHaveText('Não deu para entrar. Tente de novo.')
})

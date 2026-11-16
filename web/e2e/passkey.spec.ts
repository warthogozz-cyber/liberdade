import { expect, test } from '@playwright/test'
import { createFamily, newUser, signInWithGoogle } from './support.ts'

test('creates a passkey on this device and signs in with it', async ({ page }) => {
  const cdp = await page.context().newCDPSession(page)
  await cdp.send('WebAuthn.enable')
  const { authenticatorId } = await cdp.send('WebAuthn.addVirtualAuthenticator', {
    options: {
      protocol: 'ctap2',
      transport: 'internal',
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: true,
    },
  })

  await signInWithGoogle(page, newUser())
  await createFamily(page)
  // The new expense is full screen; Voltar leads to the screens with the navigation.
  await page.getByRole('link', { name: 'Voltar' }).click()
  const navigation = page.getByRole('navigation', { name: 'Navegação principal' })
  await navigation.getByRole('link', { name: 'Mais' }).click()
  await page.getByRole('button', { name: 'Criar passkey neste aparelho' }).click()

  await expect(page.getByRole('status')).toHaveText('Passkey criada.')
  const { credentials } = await cdp.send('WebAuthn.getCredentials', { authenticatorId })
  expect(credentials).toHaveLength(1)

  await page.getByRole('button', { name: 'Sair' }).click()
  await page.getByRole('button', { name: 'Entrar com passkey' }).click()

  await expect(page).toHaveURL('/inicio')
  await expect(navigation).toBeVisible()
})

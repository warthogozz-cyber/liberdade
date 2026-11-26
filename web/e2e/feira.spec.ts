import type { Page, Request } from '@playwright/test'
import { createFamily, expect, newUser, setModules, signInWithGoogle, test } from './support.ts'

// The service worker could answer a request before the routes below see it; blocked, every request
// goes through them.
test.use({ serviceWorkers: 'block' })

/** Every host the Feira may reach only after a tap and a notice. */
const THIRD_PARTIES = ['google.com', 'instagram.com', 'cdninstagram.com', 'facebook.com']

const isThirdParty = (url: string): boolean => {
  const host = new URL(url).hostname
  return THIRD_PARTIES.some((each) => host === each || host.endsWith(`.${each}`))
}

const MAPS_EMBED = 'https://www.google.com/maps/embed/v1/place'
const INSTAGRAM_SCRIPT = 'https://www.instagram.com/embed.js'
const POST_URL = 'https://www.instagram.com/p/Exemplo1/'

/**
 * Logs every request of the page (and its frames) and aborts the ones to a third party, so nothing
 * real leaves in any test. Call it before the first navigation.
 */
async function watchRequests(page: Page): Promise<Request[]> {
  const seen: Request[] = []
  page.on('request', (request) => seen.push(request))
  await page
    .context()
    .route('**/*', (route) =>
      isThirdParty(route.request().url()) ? route.abort() : route.continue(),
    )
  return seen
}

const thirdPartyUrls = (seen: Request[]): string[] =>
  seen.map((request) => request.url()).filter(isThirdParty)

/** The nearest fake market, which the list shows first. */
function marketLink(page: Page) {
  return page.getByRole('link', { name: /Mercado Exemplo 3/ })
}

/** A new family with every module on, on the Feira with a typed address, listing the fake markets. */
async function openFeira(page: Page): Promise<void> {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await setModules(page, true)

  await page.goto('/mais/feira')
  await expect(page.getByRole('heading', { level: 1, name: 'Feira' })).toBeVisible()
  const chooser = page.getByRole('dialog', { name: 'Onde procurar mercados?' })
  await chooser.getByRole('radio', { name: 'Digitar um endereço' }).check()
  await chooser.getByLabel('Endereço', { exact: true }).fill('Rua Exemplo')
  await chooser.getByRole('button', { name: 'Buscar', exact: true }).click()
  await chooser.getByRole('radio', { name: 'Bairro Exemplo, Cidade Exemplo' }).check()
  await chooser.getByRole('button', { name: 'Continuar', exact: true }).click()
  await expect(chooser).toBeHidden()
  await expect(marketLink(page)).toBeVisible()
}

async function openCard(page: Page): Promise<void> {
  await marketLink(page).click()
  await expect(page).toHaveURL(/\/mais\/feira\/mercado\//)
  await expect(page.getByRole('heading', { level: 1, name: 'Mercado Exemplo 3' })).toBeVisible()
}

test('noThirdPartyRequestBeforeTap: the list and the card load nothing from Google or Meta', async ({
  page,
}) => {
  const seen = await watchRequests(page)
  await openFeira(page)

  // The list shows the fake source's markets and the credit the data's license asks for.
  await expect(page.getByRole('link', { name: /Mercado Exemplo/ }).first()).toBeVisible()
  await expect(page.getByText(/colaboradores do OpenStreetMap/).first()).toBeVisible()

  await openCard(page)
  await expect(page.getByText(/colaboradores do OpenStreetMap/)).toBeVisible()
  // The map button is there (the build has a key) and nothing was loaded for it.
  await expect(page.getByRole('button', { name: 'Ver no mapa' })).toBeVisible()
  await expect(page.locator('iframe')).toHaveCount(0)

  expect(seen.length).toBeGreaterThan(0)
  expect(thirdPartyUrls(seen)).toEqual([])
})

test('mapAfterConsent: the map asks first, then asks Google for it', async ({ page }) => {
  const seen = await watchRequests(page)
  await openFeira(page)
  await openCard(page)

  await page.getByRole('button', { name: 'Ver no mapa' }).click()
  const notice = page.getByRole('alertdialog', { name: 'Mapa do Google' })
  await expect(notice).toBeVisible()
  expect(thirdPartyUrls(seen)).toEqual([])

  const mapRequest = page.waitForRequest((request) => request.url().startsWith(MAPS_EMBED))
  await notice.getByRole('button', { name: 'Abrir mapa' }).click()
  const request = await mapRequest

  expect(new URL(request.url()).searchParams.get('key')).toBe('e2e-key')
  await expect(page.locator('iframe[title="Mapa de Mercado Exemplo 3"]')).toBeVisible()
  // Aborted by the route above: nothing reached Google.
  expect(thirdPartyUrls(seen).every((url) => url.startsWith(MAPS_EMBED))).toBe(true)
})

test('instagramAfterConsent: a post shows in the isolated frame only after the notice', async ({
  page,
}) => {
  const seen = await watchRequests(page)
  await openFeira(page)
  await openCard(page)

  // A post address saved by the family (a profile has no "Ver aqui").
  await page.getByRole('button', { name: 'Editar links' }).click()
  const links = page.getByRole('dialog', { name: 'Links do mercado' })
  await links.getByLabel('Instagram (@perfil ou endereço)').fill(POST_URL)
  await links.getByRole('button', { name: 'Salvar' }).click()
  await expect(links).toBeHidden()

  await page.getByRole('button', { name: 'Ver aqui' }).click()
  const notice = page.getByRole('alertdialog', { name: 'Publicação do Instagram' })
  await expect(notice).toBeVisible()
  expect(thirdPartyUrls(seen)).toEqual([])

  const script = page.waitForRequest((request) => request.url() === INSTAGRAM_SCRIPT)
  await notice.getByRole('button', { name: 'Ver publicação' }).click()

  const frame = page.locator('iframe[title="Publicação do Instagram"]')
  await expect(frame).toBeVisible()
  await expect(frame).toHaveAttribute('src', '/embed/instagram')
  await script

  // Aborted by the route above: nothing reached Meta.
  expect(thirdPartyUrls(seen).every((url) => url === INSTAGRAM_SCRIPT)).toBe(true)
})

test('addFamilyMarket: a market added by address shows in the list', async ({ page }) => {
  await watchRequests(page)
  await openFeira(page)

  await page.getByRole('button', { name: '+ Adicionar mercado' }).click()
  const sheet = page.getByRole('dialog', { name: 'Adicionar mercado' })
  await sheet.getByLabel('Nome do mercado').fill('Mercado da Esquina')
  await sheet.getByRole('button', { name: 'Buscar endereço' }).click()
  await sheet.getByLabel('Endereço do mercado').fill('Rua Exemplo')
  await sheet.getByRole('button', { name: 'Buscar', exact: true }).click()
  await sheet.getByRole('radio', { name: 'Bairro Exemplo, Cidade Exemplo' }).check()
  await expect(sheet.getByText('Posição marcada: Bairro Exemplo, Cidade Exemplo')).toBeVisible()
  await sheet.getByRole('button', { name: 'Salvar', exact: true }).click()

  // The new card opens; back on the list, the market is there with the family's mark.
  await expect(page.getByRole('heading', { level: 1, name: 'Mercado da Esquina' })).toBeVisible()
  await page.getByRole('link', { name: 'Voltar para a Feira' }).click()
  const row = page.getByRole('link', { name: /Mercado da Esquina/ })
  await expect(row).toBeVisible()
  await expect(row).toContainText('adicionado pela família')
})

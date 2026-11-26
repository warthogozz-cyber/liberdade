import type { APIRequestContext, Page } from '@playwright/test'
import {
  buyFamilyPlan,
  createFamily,
  expect,
  newUser,
  setModules,
  signInWithGoogle,
} from './support.ts'

// The helpers the two receipt specs share: the Feira with a typed address, a fresh invented key and
// the typed-key path through the three steps. The camera is never used.

/** The nearest fake market, which the list shows first. */
export const NEAREST_MARKET = /Mercado Exemplo 3/

/** The market the receipt is registered to; its card is opened afterwards. */
export const RECEIPT_MARKET = 'Mercado Exemplo 1'

/** The invented item the flow registers. */
export const ITEM_NAME = 'Leite Exemplo 1 L'

const FIRST = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
const SECOND = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]

function digit(text: string, weights: number[]): number {
  const sum = weights.reduce(
    (total, weight, index) => total + (text.charCodeAt(index) - 48) * weight,
    0,
  )
  const rest = sum % 11
  return rest < 2 ? 0 : 11 - rest
}

/**
 * A valid invented NFC-e key (copied from `src/features/markets/receipt/testing/nfceFixtures.ts`: the
 * e2e does not import from the src helpers). The CNPJ base is invented, never a real company.
 */
function makeChave(aamm: string, serie: number, number: number): string {
  const base = '123456780001'
  const partial = base + digit(base, FIRST)
  const cnpj = partial + digit(partial, SECOND)
  const serieText = String(serie).padStart(3, '0')
  const numberText = String(number).padStart(9, '0')
  const body = `43${aamm}${cnpj}65${serieText}${numberText}112345678`
  const sum = [...body]
    .reverse()
    .reduce((total, char, index) => total + (char.charCodeAt(0) - 48) * ((index % 8) + 2), 0)
  const rest = sum % 11
  return body + (rest < 2 ? 0 : 11 - rest)
}

const randomUpTo = (max: number): number => 1 + Math.floor(Math.random() * max)

export interface FreshReceipt {
  chave: string
  /** The purchase date as the date field takes it. */
  issuedOn: string
  /** The same day as the card shows it. */
  shownOn: string
}

/**
 * A key no run has used (the series and number are random), so a retry in CI never meets "already
 * registered" on the first save. It is from last month: that is never after the current month, and
 * the date is typed because a typed key carries none.
 */
export function freshReceipt(): FreshReceipt {
  const now = new Date()
  const last = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 15))
  const year = String(last.getUTCFullYear())
  const month = String(last.getUTCMonth() + 1).padStart(2, '0')
  return {
    chave: makeChave(`${year.slice(2)}${month}`, randomUpTo(999), randomUpTo(999_999_999)),
    issuedOn: `${year}-${month}-15`,
    shownOn: `15/${month}/${year}`,
  }
}

/**
 * A new family on the Família plan (billing is on here and the Feira needs it) with every module on,
 * on the Feira with a typed address, listing the fake markets.
 */
export async function openFeira(page: Page, request: APIRequestContext): Promise<void> {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await buyFamilyPlan(page, request)
  await setModules(page, true)

  // Right after the plan and the modules change, a fresh load can still read the old ones from the
  // cache and bounce: load again until the Feira holds with the address option.
  const chooser = page.getByRole('dialog', { name: 'Onde procurar mercados?' })
  const typed = chooser.getByRole('radio', { name: 'Digitar um endereço' })
  await expect(async () => {
    await page.goto('/mais/feira')
    await expect(typed).toBeVisible({ timeout: 3_000 })
    await expect(page).toHaveURL(/\/mais\/feira$/)
  }).toPass({ timeout: 45_000 })
  await typed.check()
  await chooser.getByLabel('Endereço', { exact: true }).fill('Rua Exemplo')
  await chooser.getByRole('button', { name: 'Buscar', exact: true }).click()
  await chooser.getByRole('radio', { name: 'Bairro Exemplo, Cidade Exemplo' }).check()
  await chooser.getByRole('button', { name: 'Continuar', exact: true }).click()
  await expect(chooser).toBeHidden()
  await expect(page.getByRole('link', { name: NEAREST_MARKET })).toBeVisible()
}

/** From the Feira list to the typed key field of the first step. */
export async function openKeyField(page: Page): Promise<void> {
  await page.getByRole('link', { name: 'Registrar nota', exact: true }).click()
  await expect(page).toHaveURL(/\/mais\/feira\/nota$/)
  await page.getByRole('button', { name: 'Digitar a chave' }).click()
  await expect(page.getByLabel('Chave de 44 números')).toBeVisible()
}

/** Types the key and goes on; the caller says what comes next. */
export async function submitKey(page: Page, chave: string): Promise<void> {
  await page.getByLabel('Chave de 44 números').fill(chave)
  await page.getByRole('button', { name: 'Continuar', exact: true }).click()
}

/** The second step, as the first lookup of a new family finds it: no market owns the key's CNPJ. */
export async function expectMarketChoice(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { level: 2, name: 'O mercado' })).toBeVisible()
  await expect(page.getByText('Em qual mercado foi esta compra?')).toBeVisible()
  await expect(page.getByText('Esta nota é deste mercado')).toBeHidden()
}

/** The second step: one of the nearby markets, then on to the prices. */
export async function chooseMarket(page: Page, name: string): Promise<void> {
  await expectMarketChoice(page)
  await page.getByRole('radio', { name: new RegExp(`^${name}(?!\\d)`) }).check()
  await page.getByRole('button', { name: 'Continuar', exact: true }).click()
  await expect(page.getByRole('heading', { level: 2, name: 'Os preços' })).toBeVisible()
}

/** The third step: the date and one new item at 4,99. */
export async function fillItem(page: Page, issuedOn: string): Promise<void> {
  await page.getByLabel('Data da compra').fill(issuedOn)
  await page.getByLabel('Produto', { exact: true }).fill(ITEM_NAME)
  await page.getByRole('button', { name: `Novo: ${ITEM_NAME}` }).click()
  await page.getByLabel('Unidade', { exact: true }).selectOption('UN')
  await page.getByLabel('Preço da unidade').fill('4,99')
}

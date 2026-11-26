import type { Request } from '@playwright/test'
import {
  chooseMarket,
  fillItem,
  freshReceipt,
  ITEM_NAME,
  openFeira,
  openKeyField,
  RECEIPT_MARKET,
  submitKey,
} from './receipts.ts'
import { expect, money, test } from './support.ts'

// The service worker could answer a request before the routes below see it; blocked, every request
// goes through them.
test.use({ serviceWorkers: 'block' })

/** Only the app and its API run on this machine; anything else would have left the app. */
const isOutside = (url: string): boolean => {
  const parsed = new URL(url)
  // data: and blob: URLs have no host and never leave the page.
  return /^(https?|wss?):$/.test(parsed.protocol) && parsed.hostname !== 'localhost'
}

test('registerReceiptByHand: a typed key becomes prices on the market card, once', async ({
  page,
  request,
}) => {
  const { chave, issuedOn, shownOn } = freshReceipt()
  await openFeira(page, request)

  // From here on, every request is logged and the ones to other hosts are aborted, so nothing real
  // leaves in this test. Only those are routed: passing every request through the test as well
  // slowed WebKit past the assertions' wait.
  const seen: Request[] = []
  page.on('request', (each) => seen.push(each))
  await page.context().route(
    (url) => isOutside(url.href),
    (route) => route.abort(),
  )

  // 1-2. The key, typed.
  await openKeyField(page)
  await submitKey(page, chave)

  // 3. The market is not found: pick one of the fake ones.
  await chooseMarket(page, RECEIPT_MARKET)

  // 4. A new item, saved, and the closing sentence.
  await fillItem(page, issuedOn)
  await page.getByRole('button', { name: 'Gravar' }).click()
  await expect(
    page.getByText('Preços registrados. A nota não vira um gasto; para lançar, use Lançar.'),
  ).toBeVisible()

  // 5. The card shows the price and the day.
  await page.getByRole('link', { name: 'Ver o cartão do mercado' }).click()
  await expect(page.getByRole('heading', { level: 1, name: RECEIPT_MARKET })).toBeVisible()
  const line = page.getByRole('listitem').filter({ hasText: ITEM_NAME })
  await expect(line).toBeVisible()
  await expect(line).toContainText(money('4,99'))
  await expect(line).toContainText(shownOn)

  // 6. The same key again is refused at the first step.
  await page.getByRole('link', { name: 'Voltar para a Feira' }).click()
  await openKeyField(page)
  await submitKey(page, chave)
  await expect(page.getByText('Esta nota já foi registrada.')).toBeVisible()

  // 7. Nothing left the app, and the key never travelled in an address.
  expect(seen.length).toBeGreaterThan(0)
  expect(seen.map((each) => each.url()).filter(isOutside)).toEqual([])
  expect(seen.some((each) => each.url().includes(chave))).toBe(false)
})

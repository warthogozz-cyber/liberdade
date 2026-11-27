import {
  addToList,
  ITEM_NAME,
  openFeira,
  registerReceipt,
  RECEIPT_MARKET,
  RICE_NAME,
  SECOND_MARKET,
} from './receipts.ts'
import {
  createInvitation,
  expect,
  joinAsYouth,
  money,
  NO_SERVICE_WORKER,
  test,
  withContext,
} from './support.ts'

// The service worker could answer a request before the live notice reaches the page; blocked, every
// request goes straight to the API.
test.use({ serviceWorkers: 'block' })

/** The notice has to reach the other device well before the next time anyone would reload. */
const LIVE_TIMEOUT_MS = 10_000

/** An invented product that reads like ITEM_NAME, so the app offers to join the two. */
const NEAR_DUPLICATE = 'Leite Exemplo Integral 1 L'

const LIST_PATH = '/mais/feira/lista'

test('editShoppingListOnTwoDevices: the Youth and the Owner share one list, compare two markets and join two milks', async ({
  page: owner,
  request,
  browser,
}) => {
  test.setTimeout(240_000)

  // 0-1. A family on the Família plan with the Feira, and two invented receipts: milk at both markets,
  // rice only at the first (the e2e has no way to seed prices but the screens).
  await openFeira(owner, request)
  await registerReceipt(owner, RECEIPT_MARKET, [
    { name: ITEM_NAME, price: '4,99' },
    { name: RICE_NAME, price: '25,90' },
  ])
  await registerReceipt(owner, SECOND_MARKET, [{ name: ITEM_NAME, price: '4,99' }])

  // 2. The Youth of the same family, on another device, and both on the list.
  const link = await createInvitation(owner, 'Leo', 'Jovem')
  await withContext(
    browser,
    async (_context, youth) => {
      await joinAsYouth(youth, link, 'Leo')
      await youth.goto(LIST_PATH)
      await owner.goto(LIST_PATH)
      for (const device of [youth, owner]) {
        await expect(
          device.getByRole('heading', { level: 1, name: 'Lista de compras' }),
        ).toBeVisible()
        await expect(
          device.getByText('A lista está vazia. Adicione o que falta em casa.'),
        ).toBeVisible()
      }

      // 3. The Youth adds both products; the Owner sees them without reloading.
      await addToList(youth, ITEM_NAME)
      await addToList(youth, RICE_NAME)
      await expect(youth.getByRole('checkbox', { name: `Marcar ${RICE_NAME}` })).toBeVisible()
      await expect(owner.getByRole('checkbox', { name: `Marcar ${ITEM_NAME}` })).toBeVisible({
        timeout: LIVE_TIMEOUT_MS,
      })
      await expect(owner.getByRole('checkbox', { name: `Marcar ${RICE_NAME}` })).toBeVisible({
        timeout: LIVE_TIMEOUT_MS,
      })

      // 4. The Owner ticks the milk; the Youth sees it struck through, under "Pegos".
      // A click, not `check()`: the ticked item moves to "Pegos" and the box is drawn anew there.
      await owner.getByRole('checkbox', { name: `Marcar ${ITEM_NAME}` }).click()
      const picked = youth.getByRole('region', { name: 'Pegos' })
      await expect(picked).toBeVisible({ timeout: LIVE_TIMEOUT_MS })
      await expect(picked.getByText(ITEM_NAME, { exact: true })).toHaveClass(/line-through/)
      await expect(youth.getByRole('checkbox', { name: `Marcar ${ITEM_NAME}` })).toBeChecked()

      // 5. The Owner compares: both markets chosen, one item at both, the rice only at the first.
      await owner.getByRole('link', { name: 'Comparar mercados' }).click()
      await expect(owner).toHaveURL(/\/mais\/feira\/comparar$/)
      await expect(owner.getByRole('checkbox', { name: RECEIPT_MARKET, exact: true })).toBeChecked()
      await expect(owner.getByRole('checkbox', { name: SECOND_MARKET, exact: true })).toBeChecked()
      await expect(owner.getByText('No 1 item que os mercados têm:')).toBeVisible()
      await expect(owner.getByText(`1 item sem preço em ${SECOND_MARKET}`)).toBeVisible()
      const totals = owner.getByRole('list', { name: 'Totais' })
      for (const market of [RECEIPT_MARKET, SECOND_MARKET]) {
        await expect(totals.getByRole('listitem').filter({ hasText: market })).toContainText(
          money('4,99'),
        )
      }

      // 6. The Youth never takes the picked items off the list.
      await expect(picked.getByText(ITEM_NAME, { exact: true })).toBeVisible()
      await expect(youth.getByRole('button', { name: 'Limpar os pegos' })).toHaveCount(0)

      // 7. A second milk, typed on the list as a new product, is offered as the same product at
      // "Produtos"; the Owner joins the two and the list keeps one milk.
      await owner.goto(LIST_PATH)
      await addToList(owner, NEAR_DUPLICATE)
      await expect(owner.getByRole('checkbox', { name: `Marcar ${NEAR_DUPLICATE}` })).toBeVisible()

      await owner.goto('/mais/feira/produtos')
      const suggestion = owner
        .getByRole('listitem')
        .filter({ has: owner.getByText('São o mesmo produto?') })
      await expect(suggestion).toContainText(ITEM_NAME)
      await expect(suggestion).toContainText(NEAR_DUPLICATE)
      await suggestion.getByRole('button', { name: 'Juntar' }).click()

      const dialog = owner.getByRole('dialog', { name: 'Juntar produtos' })
      await dialog.getByRole('radio', { name: ITEM_NAME, exact: true }).check()
      await dialog.getByRole('button', { name: 'Juntar', exact: true }).click()
      await expect(dialog).toBeHidden()
      await expect(owner.getByText('São o mesmo produto?')).toHaveCount(0)
      await expect(owner.getByText(NEAR_DUPLICATE)).toHaveCount(0)

      await owner.goto(LIST_PATH)
      await expect(owner.getByRole('checkbox', { name: `Marcar ${ITEM_NAME}` })).toBeVisible()
      await expect(owner.getByRole('checkbox', { name: `Marcar ${NEAR_DUPLICATE}` })).toHaveCount(0)
      // The merged item's quantity is added to the one that stayed (1 + 1).
      await expect(owner.getByLabel(`Quantidade de ${ITEM_NAME}`)).toHaveValue('2')
    },
    NO_SERVICE_WORKER,
  )
})

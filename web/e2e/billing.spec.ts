import {
  completeGoogleSignIn,
  createFamily,
  createInvitation,
  expect,
  invitationBudget,
  money,
  newUser,
  openInvitation,
  postAsaasWebhook,
  recordExpense,
  signInBudget,
  signInWithGoogle,
  stubCheckoutHost,
  test,
  waitForQueueToEmpty,
  withContext,
} from './support.ts'

test('owner buys, becomes Founder and refunds', async ({ page, request }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await stubCheckoutHost(page)

  await page.goto('/mais')
  await page.getByRole('link', { name: 'Plano' }).click()
  await expect(page.getByRole('heading', { level: 2, name: 'Seu plano: Grátis' })).toBeVisible()
  await page.getByRole('button', { name: 'Ser Família Fundadora' }).click()
  await page.waitForURL(/checkout\.e2e\.test/)
  await expect(page.getByRole('heading', { name: 'Pagamento de teste' })).toBeVisible()
  const checkoutId = /\/c\/(chk_fake_[^/?#]+)/.exec(page.url())?.[1]
  expect(checkoutId).toBeTruthy()
  const orderId = checkoutId!.slice('chk_fake_'.length)

  await postAsaasWebhook(request, { event: 'CHECKOUT_PAID', checkout: { id: checkoutId } })

  await page.goto(`/mais/plano?pedido=${orderId}`)
  await expect(page.getByRole('heading', { name: '★ Família Fundadora' })).toBeVisible()
  await page.getByText('Oceano', { exact: true }).click()
  await expect(page.getByRole('radio', { name: 'Oceano' })).toBeChecked()

  await page.getByRole('button', { name: 'Receber meu dinheiro de volta' }).click()
  await page.getByRole('button', { name: 'Receber os R$ 89,00 de volta' }).click()
  await expect(page.getByText('Reembolso em processamento')).toBeVisible()

  await postAsaasWebhook(request, {
    event: 'PAYMENT_REFUNDED',
    payment: { id: `pay_e2e_${orderId}`, checkoutSession: checkoutId },
  })

  await page.goto('/mais/plano')
  await expect(page.getByRole('heading', { name: 'Seu plano: Grátis' })).toBeVisible()
  await expect(page.getByText('Reembolso em processamento')).toBeHidden()
})

test('adult sees the plan but no buttons', async ({ page, browser }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  const link = await createInvitation(page, 'Bia', 'Adulto')

  await withContext(browser, async (_context, bia) => {
    await openInvitation(bia, link, /convidou você para a .+ como Adulto/)
    await invitationBudget.reserve(1)
    await signInBudget.reserve(1)
    await bia.getByRole('button', { name: 'Entrar com Google' }).click()
    await completeGoogleSignIn(bia, newUser('Bia'))
    // Back from Google, the same link's page offers to join, signed in.
    await invitationBudget.reserve(1)
    await bia.getByRole('button', { name: 'Entrar na família' }).click()
    await expect(bia).toHaveURL('/inicio')

    await bia.goto('/mais/plano')
    await expect(bia.getByText('Quem cuida do plano é o Dono da família')).toBeVisible()
    await expect(bia.getByRole('button', { name: 'Ser Família Fundadora' })).toHaveCount(0)
  })
})

test('portrait shares without network', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'canShare', { value: () => false, configurable: true })
  })
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await recordExpense(page, '1234', '🛒 Mercado')
  await waitForQueueToEmpty(page)

  await page.goto('/painel')
  await expect(page.getByText(money('12,34')).first()).toBeVisible()
  await page.getByRole('link', { name: 'Retrato do mês' }).click()
  await expect(page).toHaveURL(/\/painel\/retrato/)
  const share = page.getByRole('button', { name: 'Compartilhar' })
  await expect(page.getByRole('img', { name: /^Retrato de / })).toBeVisible()
  await expect(share).toBeEnabled()

  const requests: string[] = []
  page.on('request', (req) => {
    if (/^https?:/.test(req.url())) requests.push(req.url())
  })
  const download = page.waitForEvent('download')
  await share.click()
  expect((await download).suggestedFilename()).toMatch(/^retrato-\d{4}-\d{2}\.png$/)
  await expect(page.getByText('Retrato salvo no aparelho.')).toBeVisible()
  expect(requests).toEqual([])
})

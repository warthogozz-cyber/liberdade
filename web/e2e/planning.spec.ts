import {
  createFamily,
  expect,
  familyMonth,
  newUser,
  notices,
  recordExpense,
  seedBudgetLimit,
  seedGoal,
  signInWithGoogle,
  test,
  waitForQueueToEmpty,
} from './support.ts'

test('budget crossing 80% shows the toast', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await seedBudgetLimit(page, await familyMonth(page), 'Mercado', '100.00')

  await recordExpense(page, '8500', '🛒 Mercado')
  await expect(notices(page).getByText('Mercado: chegou a 80% do limite')).toBeVisible({
    timeout: 20_000,
  })
  await waitForQueueToEmpty(page)
  // The first toast leaves by itself before the next expense, so only a new one could show up.
  await expect(notices(page).getByText('Mercado: chegou a 80% do limite')).toBeHidden({
    timeout: 20_000,
  })

  await recordExpense(page, '100', '🛒 Mercado')
  // From here the page is never reloaded: a reload would throw a late toast away and the checks
  // below could not fail. The marker proves it is the same document at the end.
  await page.evaluate(() => {
    ;(window as unknown as { sameDocument: boolean }).sameDocument = true
  })
  // Leave through the bar, and wait for the queue to sync on the list, still in the same document.
  await page.getByRole('link', { name: 'Lançamentos' }).click()
  await expect(page).toHaveURL('/lancamentos')
  await expect(page.getByText('Aguardando sinal')).toHaveCount(0, { timeout: 20_000 })
  // Back on the home the card reads 86%, so the second expense reached the budget.
  await page.getByRole('link', { name: 'Início' }).click()
  await expect(page.getByRole('meter', { name: 'Mercado' })).toHaveAttribute(
    'aria-valuenow',
    '86',
    { timeout: 20_000 },
  )
  // Give a toast the time to show, longer than the sync takes to reach the watcher.
  await page.waitForTimeout(3_000)

  await expect(notices(page).getByText(/Mercado: (chegou|passou)/)).toHaveCount(0)
  const seen = await page.evaluate(() =>
    Object.keys(localStorage).filter((key) => key.startsWith('liberdade.budget-toast:')),
  )
  expect(seen.filter((key) => key.endsWith(':80'))).toHaveLength(1)
  expect(seen.filter((key) => key.endsWith(':100'))).toHaveLength(0)
  expect(
    await page.evaluate(() => (window as unknown as { sameDocument?: boolean }).sameDocument),
  ).toBe(true)
})

test('closing the month copies limits', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await seedBudgetLimit(page, await familyMonth(page, -1), 'Mercado', '500.00')

  await page.goto('/inicio')
  const card = page
    .locator('section')
    .filter({ has: page.getByRole('heading', { level: 2, name: 'Orçamento do mês' }) })
  await expect(card.getByText(/: 1 de 1 categorias dentro do limite/)).toBeVisible()
  await card.getByRole('button', { name: 'Copiar limites' }).click()
  await expect(card.getByRole('button', { name: 'Copiar limites' })).toBeHidden()

  await card.getByRole('link', { name: 'Ver orçamento' }).click()
  await expect(page).toHaveURL('/planejar/orcamento')
  await expect(page.getByRole('heading', { level: 1, name: 'Orçamento' })).toBeVisible()
  await expect(page.getByRole('button', { name: /Mercado/ })).toBeVisible()
  await expect(
    page.getByRole('main').getByRole('listitem').filter({ hasText: 'Mercado' }),
  ).toContainText(/R\$\s500,00/)
})

test('goal reached celebrates once', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  await seedGoal(page, 'Viagem', '100.00')

  await page.goto('/planejar/metas')
  await page.getByRole('button', { name: /Viagem/ }).click()
  await page.getByRole('button', { name: 'Guardar', exact: true }).click()
  const sheet = page.getByRole('dialog', { name: 'Guardar na meta Viagem' })
  await sheet.getByLabel('Valor', { exact: true }).fill('100,00')
  await sheet.getByRole('button', { name: 'Guardar', exact: true }).click()
  await expect(page.getByText('Meta atingida: Viagem!')).toBeVisible()

  await page.reload()
  await expect(page.getByRole('heading', { level: 1, name: 'Metas' })).toBeVisible()
  await expect(page.getByRole('button', { name: /Viagem/ })).toContainText('Atingida')
  await expect(page.getByText(/Meta atingida/)).toHaveCount(0)
})

// The e2e profile turns billing on (`liberdade.billing.enabled=true`), and a new family is on the
// free plan, so the limit applies here.
test('free plan goal limit', async ({ page }) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  for (const name of ['Casa', 'Carro', 'Festa']) await seedGoal(page, name, '1000.00')

  await page.goto('/planejar/metas')
  await expect(page.getByRole('button', { name: /Festa/ })).toBeVisible()
  await page.getByRole('button', { name: 'Nova meta' }).click()
  await expect(
    page
      .getByRole('dialog', { name: 'Nova meta' })
      .getByText(/No plano Grátis, a família tem até 3 metas ativas/),
  ).toBeVisible()
})

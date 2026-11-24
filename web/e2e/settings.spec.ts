import { createFamily, expect, newUser, signInWithGoogle, test, typeAmount } from './support.ts'

test('soundOffByDefaultAndPersists: the achievement sound starts off and stays on after a reload', async ({
  page,
}) => {
  await signInWithGoogle(page, newUser())
  await createFamily(page)

  await page.goto('/mais/ajustes')
  await expect(page.getByRole('heading', { level: 1, name: 'Ajustes' })).toBeVisible()
  const sound = page.getByRole('switch', { name: 'Sons de conquista' })
  await expect(sound).toHaveAttribute('aria-checked', 'false')

  await sound.click()
  await expect(sound).toHaveAttribute('aria-checked', 'true')

  await page.reload()
  await expect(page.getByRole('switch', { name: 'Sons de conquista' })).toHaveAttribute(
    'aria-checked',
    'true',
  )
})

test('reducedMotionDoesNotAnimate: a sheet opens without its slide-in animation', async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await signInWithGoogle(page, newUser())
  await createFamily(page)

  await page.goto('/lancar')
  await typeAmount(page, '100')
  await page.getByRole('button', { name: /^(Hoje|Ontem|\d+ de \p{L}+\.?) · .+ · Eu/u }).click()
  const sheet = page.getByRole('dialog')
  await expect(sheet).toBeVisible()

  const animationName = await sheet.evaluate((el) => getComputedStyle(el).animationName)
  expect(animationName).toBe('none')
})

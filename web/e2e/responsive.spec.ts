import type { Page } from '@playwright/test'
import {
  buyFamilyPlan,
  createFamily,
  expect,
  newUser,
  setModules,
  signInWithGoogle,
  test,
} from './support.ts'

// Only the phone projects: a desktop screen has room for everything these checks look at.
test.skip(({ isMobile }) => !isMobile, 'Checks the layout on a phone screen')

/**
 * The page's HTML elements whose content is wider than their own box (a word pushed out of a tab, a
 * field wider than its form), plus the page itself when it scrolls sideways. Boxes made to scroll,
 * inline text and form controls are left out; so is SVG, whose labels draw outside their box on purpose.
 */
function clippedElements(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const found: string[] = []
    const root = document.documentElement
    if (root.scrollWidth > root.clientWidth) {
      found.push(`page ${root.scrollWidth}px wide on a ${root.clientWidth}px screen`)
    }
    for (const element of document.body.querySelectorAll('*')) {
      if (!(element instanceof HTMLElement) || element.clientWidth === 0) continue
      if (['INPUT', 'TEXTAREA', 'SELECT'].includes(element.tagName)) continue
      const style = getComputedStyle(element)
      if (style.display === 'inline' || /auto|scroll/.test(style.overflowX)) continue
      if (element.scrollWidth > element.clientWidth + 1) {
        const text = (element.textContent ?? '').trim().slice(0, 40)
        found.push(
          `${element.tagName.toLowerCase()} "${text}" ${element.scrollWidth}px in ${element.clientWidth}px`,
        )
      }
    }
    return found
  })
}

/** Opens the screen (again while a module switched on a moment ago still bounces) until it holds. */
async function open(page: Page, path: string): Promise<void> {
  await expect(async () => {
    await page.goto(path)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: 3_000 })
    await expect(page).toHaveURL(new RegExp(`${path}$`))
  }).toPass({ timeout: 45_000 })
}

const SCREENS = [
  '/inicio',
  '/lancamentos',
  '/lancar',
  '/painel',
  '/planejar',
  '/planejar/metas',
  '/planejar/orcamento',
  '/planejar/reserva',
  '/mais',
  '/mais/contas',
  '/mais/categorias',
  '/mais/familia',
  '/mais/indicadores',
  '/mais/conquistas',
  '/mais/feira',
  '/mais/plano',
  '/mais/privacidade',
]

/** The bottom bar's tabs: none wider than its cell, and all the same height (no label on an extra line). */
async function expectBottomBarFits(page: Page): Promise<void> {
  const tabs = page.getByRole('navigation', { name: 'Navegação principal' }).getByRole('link')
  await expect(tabs).toHaveCount(5)
  const sizes = await tabs.evaluateAll((links) =>
    links.map((link) => ({
      name: link.textContent,
      overflows: link.scrollWidth > link.clientWidth,
      height: link.getBoundingClientRect().height,
    })),
  )
  expect(sizes.filter((size) => size.overflows).map((size) => size.name)).toEqual([])
  expect(new Set(sizes.map((size) => size.height)).size, JSON.stringify(sizes)).toBe(1)
}

test('everyScreenFits: the main screens fit a phone without clipped text', async ({
  page,
  request,
}) => {
  test.setTimeout(180_000)
  await signInWithGoogle(page, newUser())
  await createFamily(page)
  // The Família plan and every module on: five tabs in the bottom bar and every section shown.
  await buyFamilyPlan(page, request)
  await setModules(page, true)

  for (const path of SCREENS) {
    await open(page, path)
    expect(await clippedElements(page), path).toEqual([])
  }
  await expectBottomBarFits(page)
})

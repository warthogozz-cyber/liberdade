import { describe, expect, it } from 'vitest'
import type { CategoryView } from '@/shared/api'
import { COLOR_DOT } from './colorDot'
import { CATEGORY_COLORS, CATEGORY_ICONS, categoryTree } from './categoryStyle'

function category(overrides: Partial<CategoryView> & { name: string }): CategoryView {
  return {
    id: crypto.randomUUID(),
    kind: 'EXPENSE',
    icon: null,
    color: null,
    parentId: null,
    archivedAt: null,
    ...overrides,
  }
}

describe('categoryTree', () => {
  it('treeNestsChildren', () => {
    const market = category({ name: 'Mercado' })
    const fair = category({ name: 'Feira', parentId: market.id })
    const leisure = category({ name: 'Lazer' })
    const salary = category({ name: 'Salário', kind: 'INCOME' })

    const tree = categoryTree([market, leisure, fair, salary], 'EXPENSE')

    expect(tree).toHaveLength(2)
    expect(tree[0].category).toBe(market)
    expect(tree[0].children).toEqual([fair])
    expect(tree[1]).toEqual({ category: leisure, children: [] })
  })

  it('keeps the API order and shows a child whose parent is not in the list on its own', () => {
    const parent = category({ name: 'Mercado' })
    const orphan = category({ name: 'Feira', parentId: parent.id })
    const other = category({ name: 'Lazer' })

    expect(categoryTree([orphan, other], 'EXPENSE').map((entry) => entry.category.name)).toEqual([
      'Feira',
      'Lazer',
    ])
  })
})

describe('the palette', () => {
  it('eightColorsInOrder', () => {
    const keys = ['TEAL', 'BLUE', 'INDIGO', 'PURPLE', 'PINK', 'RED', 'ORANGE', 'GREEN']

    expect(CATEGORY_COLORS.map((entry) => entry.color)).toEqual(keys)
    expect(Object.keys(COLOR_DOT)).toEqual(keys)
    expect(CATEGORY_COLORS.map((entry) => entry.dot)).toEqual(Object.values(COLOR_DOT))
    expect(CATEGORY_ICONS).toHaveLength(24)
    expect(new Set(CATEGORY_ICONS.map((entry) => entry.icon)).size).toBe(24)
    expect(new Set(CATEGORY_ICONS.map((entry) => entry.labelId)).size).toBe(24)
    expect(CATEGORY_ICONS[0].icon).toBe('🛒')
    expect(CATEGORY_ICONS[23].icon).toBe('📈')
  })
})

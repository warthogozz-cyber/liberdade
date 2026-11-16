import type { CategoryColor, CategoryKind, CategoryView } from '@/shared/api'
import { COLOR_DOT } from './colorDot'

type MessageId = FormatjsIntl.Message['ids']

interface ColorInfo {
  color: CategoryColor
  labelId: MessageId
  /** The dot's classes, for both themes. */
  dot: string
}

/** The palette, in the API's order. The dot is decorative: a chip always has the icon and the name. */
export const CATEGORY_COLORS = [
  { color: 'TEAL', labelId: 'categories.color.teal', dot: COLOR_DOT.TEAL },
  { color: 'BLUE', labelId: 'categories.color.blue', dot: COLOR_DOT.BLUE },
  { color: 'INDIGO', labelId: 'categories.color.indigo', dot: COLOR_DOT.INDIGO },
  { color: 'PURPLE', labelId: 'categories.color.purple', dot: COLOR_DOT.PURPLE },
  { color: 'PINK', labelId: 'categories.color.pink', dot: COLOR_DOT.PINK },
  { color: 'RED', labelId: 'categories.color.red', dot: COLOR_DOT.RED },
  { color: 'ORANGE', labelId: 'categories.color.orange', dot: COLOR_DOT.ORANGE },
  { color: 'GREEN', labelId: 'categories.color.green', dot: COLOR_DOT.GREEN },
] as const satisfies readonly ColorInfo[]

interface IconInfo {
  icon: string
  /** The name a screen reader says for the emoji. */
  labelId: MessageId
}

/** The icons a category can take, in the order they are offered. */
export const CATEGORY_ICONS = [
  { icon: '🛒', labelId: 'categories.icon.cart' },
  { icon: '🍽️', labelId: 'categories.icon.restaurant' },
  { icon: '🏠', labelId: 'categories.icon.house' },
  { icon: '💡', labelId: 'categories.icon.energy' },
  { icon: '🚗', labelId: 'categories.icon.car' },
  { icon: '🚌', labelId: 'categories.icon.bus' },
  { icon: '⛽', labelId: 'categories.icon.fuel' },
  { icon: '💊', labelId: 'categories.icon.pill' },
  { icon: '🏥', labelId: 'categories.icon.health' },
  { icon: '🎓', labelId: 'categories.icon.school' },
  { icon: '📚', labelId: 'categories.icon.books' },
  { icon: '👕', labelId: 'categories.icon.clothes' },
  { icon: '🎁', labelId: 'categories.icon.gift' },
  { icon: '🎮', labelId: 'categories.icon.games' },
  { icon: '🎬', labelId: 'categories.icon.cinema' },
  { icon: '✈️', labelId: 'categories.icon.travel' },
  { icon: '🐾', labelId: 'categories.icon.pet' },
  { icon: '👶', labelId: 'categories.icon.child' },
  { icon: '💇', labelId: 'categories.icon.beauty' },
  { icon: '📱', labelId: 'categories.icon.phone' },
  { icon: '🧾', labelId: 'categories.icon.bills' },
  { icon: '💰', labelId: 'categories.icon.money' },
  { icon: '💼', labelId: 'categories.icon.work' },
  { icon: '📈', labelId: 'categories.icon.investments' },
] as const satisfies readonly IconInfo[]

export interface CategoryNode {
  category: CategoryView
  children: CategoryView[]
}

/**
 * The categories of `kind` as a tree, in the order given: each top-level category with its
 * subcategories under it. A subcategory whose parent is not in the list stands on its own, as an
 * archived one does under an active parent.
 */
export function categoryTree(categories: CategoryView[], kind: CategoryKind): CategoryNode[] {
  const ofKind = categories.filter((category) => category.kind === kind)
  const ids = new Set(ofKind.map((category) => category.id))
  const childrenOf = new Map<string, CategoryView[]>()
  for (const category of ofKind) {
    if (category.parentId !== null && ids.has(category.parentId)) {
      childrenOf.set(category.parentId, [...(childrenOf.get(category.parentId) ?? []), category])
    }
  }
  return ofKind
    .filter((category) => category.parentId === null || !ids.has(category.parentId))
    .map((category) => ({ category, children: childrenOf.get(category.id) ?? [] }))
}

/** The id of a kind's tab, so the panel can be named after the chosen one. */
export function categoryTabId(panelId: string, kind: CategoryKind): string {
  return `${panelId}-tab-${kind.toLowerCase()}`
}

import type { BudgetLineView } from '@/shared/api/planning'

type Money = BudgetLineView['limit']

export type BudgetToast = {
  message: 'near' | 'at' | 'over'
  categoryId: string
  overBy?: Money
  /** The thresholds this toast covers, as `<categoryId>:<80|100>`, to mark as seen once it shows. */
  keys: string[]
}

const RANK = { near: 1, at: 2, over: 3 } as const

function toastForLine(line: BudgetLineView, seen: (key: string) => boolean): BudgetToast | null {
  const at80 = `${line.categoryId}:80`
  const at100 = `${line.categoryId}:100`
  if (line.state === 'OVER' || line.state === 'AT') {
    if (seen(at100)) return null
    return line.state === 'OVER' && line.overBy
      ? { message: 'over', categoryId: line.categoryId, overBy: line.overBy, keys: [at100, at80] }
      : { message: 'at', categoryId: line.categoryId, keys: [at100, at80] }
  }
  if (line.state === 'NEAR' && !seen(at80)) {
    return { message: 'near', categoryId: line.categoryId, keys: [at80] }
  }
  return null
}

/**
 * The notice a saved expense earns, if any (D18): looks at the limit of its category and of the
 * category's parent, whose limit counts what the children spend. Reaching 100% wins over 80% and
 * marks both thresholds, so a jump past the limit does not warn twice. `seen` tells whether a key
 * was already shown.
 */
export function budgetToastFor(
  lines: readonly BudgetLineView[],
  categoryId: string,
  parentId: string | null,
  seen: (key: string) => boolean,
): BudgetToast | null {
  let best: BudgetToast | null = null
  for (const id of parentId === null ? [categoryId] : [categoryId, parentId]) {
    const line = lines.find((each) => each.categoryId === id)
    const toast = line ? toastForLine(line, seen) : null
    if (toast && (best === null || RANK[toast.message] > RANK[best.message])) best = toast
  }
  return best
}

/** `liberdade.budget-toast:<ws>:<YYYY-MM>:<categoryId>:<80|100>`; `key` is `<categoryId>:<80|100>`. */
export function toastStorageKey(workspaceId: string, month: string, key: string): string {
  return `liberdade.budget-toast:${workspaceId}:${month}:${key}`
}

/** Whether the notice was shown; storage that fails reads as not shown. */
export function readToastSeen(storageKey: string): boolean {
  try {
    return localStorage.getItem(storageKey) !== null
  } catch {
    return false
  }
}

/** Remembers a shown notice; storage that fails only means it may show again. */
export function writeToastSeen(storageKey: string): void {
  try {
    localStorage.setItem(storageKey, '1')
  } catch {
    // Without storage the notice may repeat; nothing else depends on it.
  }
}

import type { BudgetLineView } from '@/shared/api/planning'
import { parseCents } from '@/shared/money'

/** How many limits a month kept: "within" is `spent <= limit`, so reaching the limit counts. */
export function monthSummary(lines: readonly BudgetLineView[]): { within: number; total: number } {
  const within = lines.filter(
    (line) => parseCents(line.spent.amount) <= parseCents(line.limit.amount),
  ).length
  return { within, total: lines.length }
}

/** `liberdade.budget-summary:<ws>:<YYYY-MM of the current month>`. */
export function summaryStorageKey(workspaceId: string, month: string): string {
  return `liberdade.budget-summary:${workspaceId}:${month}`
}

/** Whether the summary was answered this month; storage that fails reads as not answered. */
export function summaryAnswered(storageKey: string): boolean {
  try {
    return localStorage.getItem(storageKey) !== null
  } catch {
    return false
  }
}

/** Remembers that the summary was answered. */
export function markSummaryAnswered(storageKey: string): void {
  try {
    localStorage.setItem(storageKey, '1')
  } catch {
    // Without storage the card comes back next time; nothing else depends on it.
  }
}

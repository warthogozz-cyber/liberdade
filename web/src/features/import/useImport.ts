import { useQueryClient } from '@tanstack/react-query'
import { importStatement } from '@/shared/api/endpoints'
import { queryKeys } from '@/shared/api/queryKeys'
import { planningKeys } from '@/shared/api/planning'
import { useWorkspaces } from '@/shared/api/hooks'
import type { ImportRow } from '@/shared/api/types'
import { centsToApi, DEFAULT_CURRENCY } from '@/shared/money/money'
import type { PreviewLine } from './preview'

/** The API takes at most this many lines per call. */
const BATCH_SIZE = 500
const MAX_DESCRIPTION_LENGTH = 140

export type ImportTotals = {
  created: number
  confirmed: number
  skipped: number
  /** How many lines were already sent when a batch failed; `null` when every batch went through. */
  failedAfter: number | null
}

function toRow(line: PreviewLine, currency: string): ImportRow {
  return {
    id: line.id,
    kind: line.kind,
    amount: { amount: centsToApi(line.absCents), currency },
    occurredOn: line.occurredOn,
    description: line.description.trim().slice(0, MAX_DESCRIPTION_LENGTH),
    categoryId: line.categoryId as string,
    importKey: line.importKey,
    confirmsPlannedId: line.confirmsPlannedId,
  }
}

/**
 * Sends the checked lines in batches of 500, one after the other, with the typed client (never the offline
 * queue). A failed batch stops the rest and `failedAfter` says how many lines had gone; sending again is safe,
 * the API counts what it already has as skipped. The family's transactions, accounts, dashboard and planning
 * are refetched at the end, whatever happened.
 */
export function useImport(ws: string, accountId: string) {
  const queryClient = useQueryClient()
  const workspaces = useWorkspaces()
  const currency = workspaces.find((w) => w.id === ws)?.currency ?? DEFAULT_CURRENCY

  async function send(lines: PreviewLine[]): Promise<ImportTotals> {
    const rows = lines.filter((l) => l.checked).map((l) => toRow(l, currency))
    const totals: ImportTotals = { created: 0, confirmed: 0, skipped: 0, failedAfter: null }
    for (let start = 0; start < rows.length; start += BATCH_SIZE) {
      try {
        const result = await importStatement(ws, accountId, rows.slice(start, start + BATCH_SIZE))
        totals.created += result.created
        totals.confirmed += result.confirmed
        totals.skipped += result.skipped
      } catch {
        totals.failedAfter = start
        break
      }
    }
    if (rows.length > 0) {
      const family = queryKeys.workspace(ws)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.accounts(ws) }),
        queryClient.invalidateQueries({ queryKey: [...family, 'transactions'] }),
        queryClient.invalidateQueries({ queryKey: [...family, 'dashboard'] }),
        queryClient.invalidateQueries({ queryKey: planningKeys.all(ws) }),
      ])
    }
    return totals
  }

  return { send }
}

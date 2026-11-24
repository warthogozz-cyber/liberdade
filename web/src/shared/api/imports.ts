import { client, unwrap } from './client'
import type { CategoryHint, CategoryHintItem, ImportResult, ImportRow } from './types'

// Apart from `endpoints.ts`, so only the lazy statement import loads these calls.

/** Sends up to 500 confirmed statement lines. A resend counts the lines already saved as `skipped`. */
export function importStatement(
  ws: string,
  accountId: string,
  rows: ImportRow[],
): Promise<ImportResult> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/accounts/{accountId}/imports', {
      params: { path: { ws, accountId } },
      body: { rows },
    }),
  )
}

/** The category each past `(kind, description)` used, in the order asked; `null` when none fits. */
export async function categoryHints(
  ws: string,
  items: CategoryHintItem[],
): Promise<CategoryHint[]> {
  const { hints } = await unwrap(
    client.POST('/v1/workspaces/{ws}/imports/category-hints', {
      params: { path: { ws } },
      body: { items },
    }),
  )
  return hints
}

/**
 * What only the planning screens need: the family's recurring rules, and confirming or skipping a
 * planned transaction. The `shared/api` barrel does not re-export this file, so none of it weighs on
 * the initial load. Every call needs the connection: none goes through the offline queue.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { emitLocalAction } from '@/shared/events'
import { markLocalChange } from '@/shared/live/localChanges'
import { client, unwrap } from './client'
import { queryKeys } from './queryKeys'
import type {
  ConfirmTransactionRequest,
  CreateRecurringRuleRequest,
  RecurringRuleView,
  TransactionView,
  UpdateRecurringRuleRequest,
} from './types'

/** The family's active rules (a Youth gets the ones on their accounts). */
export function listRules(ws: string): Promise<RecurringRuleView[]> {
  return unwrap(client.GET('/v1/workspaces/{ws}/recurring-rules', { params: { path: { ws } } }))
}

/** Creates a rule, which generates its first planned transactions. */
export function createRule(
  ws: string,
  body: CreateRecurringRuleRequest,
): Promise<RecurringRuleView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/recurring-rules', { params: { path: { ws } }, body }),
  )
}

/** Replaces a rule, with the `version` it was opened at. */
export function updateRule(
  ws: string,
  id: string,
  body: UpdateRecurringRuleRequest,
): Promise<RecurringRuleView> {
  return unwrap(
    client.PATCH('/v1/workspaces/{ws}/recurring-rules/{id}', {
      params: { path: { ws, id } },
      body,
    }),
  )
}

/** Archives a rule; archiving it again changes nothing. */
export async function archiveRule(ws: string, id: string): Promise<void> {
  await unwrap(
    client.POST('/v1/workspaces/{ws}/recurring-rules/{id}/archive', {
      params: { path: { ws, id } },
    }),
  )
}

/** Confirms a planned transaction, as it is or with the adjustments in `body`. */
export function confirmPlanned(
  ws: string,
  id: string,
  body: ConfirmTransactionRequest,
): Promise<TransactionView> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/transactions/{id}/confirm', {
      params: { path: { ws, id } },
      body,
    }),
  )
}

/** Skips a planned transaction: it is deleted, and the restore route brings it back. */
export async function skipPlanned(ws: string, id: string, version: number): Promise<void> {
  await unwrap(
    client.POST('/v1/workspaces/{ws}/transactions/{id}/skip', {
      params: { path: { ws, id } },
      body: { version },
    }),
  )
}

/** The family's active recurring rules; waits for a family id. */
export function useRules(ws: string) {
  return useQuery({
    queryKey: queryKeys.rules(ws),
    queryFn: () => listRules(ws),
    enabled: ws !== '',
  })
}

/** A rule, a planned row or a total may change with any of these calls, so the family refreshes. */
function useRefreshWorkspace(ws: string) {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: queryKeys.workspace(ws) })
}

/** Creates a recurring rule. */
export function useCreateRule(ws: string) {
  return useMutation({
    mutationFn: (body: CreateRecurringRuleRequest) => createRule(ws, body),
    onSettled: useRefreshWorkspace(ws),
  })
}

/** Saves an edit of a rule. */
export function useUpdateRule(ws: string) {
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateRecurringRuleRequest }) =>
      updateRule(ws, id, body),
    onSettled: useRefreshWorkspace(ws),
  })
}

/** Archives a rule. */
export function useArchiveRule(ws: string) {
  return useMutation({
    mutationFn: (id: string) => archiveRule(ws, id),
    onSettled: useRefreshWorkspace(ws),
  })
}

/** Confirms a planned transaction, and tells the device's listeners it is now a saved expense (D17). */
export function useConfirmPlanned(ws: string) {
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: ConfirmTransactionRequest }) => {
      markLocalChange(id)
      return confirmPlanned(ws, id, body)
    },
    onSuccess: (confirmed) =>
      emitLocalAction({
        type: 'transaction-saved',
        workspaceId: ws,
        ...(confirmed.categoryId ? { categoryId: confirmed.categoryId } : {}),
        month: confirmed.occurredOn.slice(0, 7),
        fromQueue: false,
      }),
    onSettled: useRefreshWorkspace(ws),
  })
}

/** Skips a planned transaction, at the `version` it was shown at. */
export function useSkipPlanned(ws: string) {
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) => skipPlanned(ws, id, version),
    onSettled: useRefreshWorkspace(ws),
  })
}

/**
 * What only lazy screens need: reading one transaction by id and setting an account's owner. The
 * `shared/api` barrel does not re-export this file, so none of it weighs on the initial load.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { client, unwrap } from './client'
import { queryKeys } from './queryKeys'
import type { AccountView, TransactionView } from './types'

/** One transaction's key: under the family's, so a refresh of the family reaches it. */
export const transactionKey = (ws: string, id: string) =>
  [...queryKeys.workspace(ws), 'transactions', id] as const

/** Reads one transaction. A deleted one is a 409 `transaction.deleted`, an unknown one a 404. */
export function getTransaction(ws: string, id: string): Promise<TransactionView> {
  return unwrap(
    client.GET('/v1/workspaces/{ws}/transactions/{id}', { params: { path: { ws, id } } }),
  )
}

/** Sets the account's owner, or clears it with `null`. */
export function setAccountOwner(
  ws: string,
  id: string,
  membershipId: string | null,
): Promise<AccountView> {
  return unwrap(
    client.PUT('/v1/workspaces/{ws}/accounts/{id}/owner', {
      params: { path: { ws, id } },
      body: { membershipId },
    }),
  )
}

/** One transaction, always read from the server: a copy in a list may be behind. */
export function useTransaction(ws: string, id: string) {
  return useQuery({
    queryKey: transactionKey(ws, id),
    queryFn: () => getTransaction(ws, id),
    staleTime: 0,
    // Nothing of it outlives the screen: not in memory, and not on the device either.
    gcTime: 0,
    meta: { persist: false },
  })
}

/** Sets who an account belongs to; `null` clears it. */
export function useSetAccountOwner(ws: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, membershipId }: { id: string; membershipId: string | null }) =>
      setAccountOwner(ws, id, membershipId),
    onSettled: async () => {
      // Only what shows owners: the accounts, and the dashboard's account list.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.accounts(ws) }),
        queryClient.invalidateQueries({ queryKey: [...queryKeys.workspace(ws), 'dashboard'] }),
      ])
    },
  })
}

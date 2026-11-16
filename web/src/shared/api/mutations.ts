import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deleteTransaction } from './endpoints'
import { queryKeys } from './queryKeys'

/** Deletes one of the family's transactions, then refreshes everything the family owns. */
export function useDeleteTransaction(ws: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteTransaction(ws, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.workspace(ws) }),
  })
}

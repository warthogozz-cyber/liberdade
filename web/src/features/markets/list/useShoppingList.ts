import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import {
  addListItem,
  clearCheckedItems,
  getShoppingList,
  marketsKeys,
  removeListItem,
  reorderList,
  updateListItem,
  type AddListItemBody,
  type ShoppingListItem,
  type UpdateListItemBody,
} from '@/shared/api/markets'

/** The shopping list, in the family's order. */
export function useShoppingList(ws: string | undefined) {
  return useQuery({
    queryKey: marketsKeys.list(ws ?? ''),
    queryFn: () => getShoppingList(ws as string),
    enabled: ws !== undefined,
  })
}

/** Reads the list again, and the comparison that is built from it. */
function refreshList(queryClient: QueryClient, ws: string) {
  const root = marketsKeys.all(ws)
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: [...root, 'list'] }),
    queryClient.invalidateQueries({ queryKey: [...root, 'compare'] }),
  ])
}

/** A change that may also create a product reads everything the Feira has read. */
function useRefreshing<Variables, Result>(
  ws: string,
  mutationFn: (variables: Variables) => Promise<Result>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: marketsKeys.all(ws) }),
  })
}

export const useAddListItem = (ws: string) =>
  useRefreshing(ws, (body: AddListItemBody) => addListItem(ws, body))

export const useRemoveListItem = (ws: string) =>
  useRefreshing(ws, (id: string) => removeListItem(ws, id))

export const useClearChecked = (ws: string) => useRefreshing(ws, () => clearCheckedItems(ws))

/**
 * Marking and the quantity show at once: the list is changed in the cache first and put back when the
 * API refuses, then read again either way.
 */
export function useUpdateListItem(ws: string) {
  const queryClient = useQueryClient()
  const key = marketsKeys.list(ws)
  return useMutation({
    mutationFn: (change: { id: string; body: UpdateListItemBody }) =>
      updateListItem(ws, change.id, change.body),
    onMutate: async (change) => {
      await queryClient.cancelQueries({ queryKey: key })
      const before = queryClient.getQueryData<ShoppingListItem[]>(key)
      queryClient.setQueryData<ShoppingListItem[]>(key, (items) =>
        items?.map((each) =>
          each.id === change.id
            ? {
                ...each,
                checked: change.body.checked ?? each.checked,
                quantity: change.body.quantity ?? each.quantity,
              }
            : each,
        ),
      )
      return { before }
    },
    onError: (_error, _change, context) => queryClient.setQueryData(key, context?.before),
    onSettled: () => refreshList(queryClient, ws),
  })
}

/** Sends the new order. A 409 (another device changed the list) is the caller's to explain. */
export function useReorderList(ws: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (ids: string[]) => reorderList(ws, ids),
    onSettled: () => refreshList(queryClient, ws),
  })
}

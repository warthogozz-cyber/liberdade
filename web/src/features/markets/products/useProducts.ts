import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  dismissPair,
  editProduct,
  getProductsOverview,
  getProductSuggestions,
  marketsKeys,
  mergeProducts,
  type EditProductBody,
} from '@/shared/api/markets'

/** Every product with its last price at each market. */
export function useProductsOverview(ws: string | undefined) {
  return useQuery({
    queryKey: marketsKeys.productsOverview(ws ?? ''),
    queryFn: () => getProductsOverview(ws as string),
    enabled: ws !== undefined,
  })
}

/** The pairs that may be one product; off for the people who cannot join them. */
export function useProductSuggestions(ws: string | undefined, enabled = true) {
  return useQuery({
    queryKey: marketsKeys.productSuggestions(ws ?? ''),
    queryFn: () => getProductSuggestions(ws as string),
    enabled: ws !== undefined && enabled,
  })
}

/** A change to products reads the products, the suggestions, the search and the list again. */
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

export const useEditProduct = (ws: string) =>
  useRefreshing(ws, (change: { id: string; body: EditProductBody }) =>
    editProduct(ws, change.id, change.body),
  )

/** `keepId` stays and `removeId` is joined into it. */
export const useMergeProducts = (ws: string) =>
  useRefreshing(ws, (join: { keepId: string; removeId: string }) =>
    mergeProducts(ws, join.keepId, join.removeId),
  )

export const useDismissPair = (ws: string) =>
  useRefreshing(ws, (pair: { a: string; b: string }) => dismissPair(ws, pair.a, pair.b))

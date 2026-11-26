import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getFamilyProducts,
  getMarketPrices,
  lookupReceipt,
  marketsKeys,
  saveReceipt,
  type ReceiptLookupBody,
  type SaveReceiptBody,
} from '@/shared/api/markets'

/** Asks the API about a key, on the person's tap. Nothing is saved, so nothing is refreshed. */
export const useReceiptLookup = (ws: string) =>
  useMutation({ mutationFn: (body: ReceiptLookupBody) => lookupReceipt(ws, body) })

/** Saves a receipt; the cards, prices and product search are read again afterwards. */
export function useSaveReceipt(ws: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: SaveReceiptBody) => saveReceipt(ws, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: marketsKeys.all(ws) }),
  })
}

/** The last price of each product at a market. */
export function useMarketPrices(ws: string | undefined, ref: string | undefined) {
  return useQuery({
    queryKey: marketsKeys.prices(ws ?? '', ref ?? ''),
    queryFn: () => getMarketPrices(ws as string, ref as string),
    enabled: ws !== undefined && ref !== undefined,
  })
}

/** The family's products, narrowed by what the person types (`q`). */
export function useFamilyProducts(ws: string | undefined, q: string) {
  return useQuery({
    queryKey: marketsKeys.products(ws ?? '', q),
    queryFn: () => getFamilyProducts(ws as string, q),
    enabled: ws !== undefined,
  })
}

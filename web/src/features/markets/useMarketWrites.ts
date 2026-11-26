import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useIntl } from 'react-intl'
import { ApiError } from '@/shared/api'
import {
  archiveMarket,
  convertMarket,
  createMarket,
  marketsKeys,
  putMarketLinks,
  putMarketPosition,
  type MarketLinksBody,
  type MarketPoint,
  type NewMarketBody,
} from '@/shared/api/markets'

/** Every write of the Feira refreshes what the Feira has read (the lists and the cards). */
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

export const useSaveLinks = (ws: string, ref: string) =>
  useRefreshing(ws, (body: MarketLinksBody) => putMarketLinks(ws, ref, body))

export const useAddMarket = (ws: string) =>
  useRefreshing(ws, (body: NewMarketBody) => createMarket(ws, body))

export const useSetPosition = (ws: string, ref: string) =>
  useRefreshing(ws, (body: MarketPoint) => putMarketPosition(ws, ref, body))

export const useArchiveMarket = (ws: string, ref: string) =>
  useRefreshing(ws, () => archiveMarket(ws, ref))

export const useConvertMarket = (ws: string, ref: string) =>
  useRefreshing(ws, (body: NewMarketBody) => convertMarket(ws, ref, body))

/** What to tell the person about a failed write: the API's own message, else the generic one. */
export function useWriteError(failure: { isError: boolean; error: unknown }): string | null {
  const intl = useIntl()
  if (!failure.isError) return null
  return failure.error instanceof ApiError && failure.error.detail
    ? failure.error.detail
    : intl.formatMessage({ id: 'markets.form.saveFailed' })
}

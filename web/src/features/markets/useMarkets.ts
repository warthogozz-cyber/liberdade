import { useQuery } from '@tanstack/react-query'
import {
  getMarketCard,
  getMarketHome,
  getMarketsSettings,
  marketsKeys,
  searchMarkets,
  type SearchArea,
} from '@/shared/api/markets'
import { roundTo, type GeoPoint } from './geo'

/** The default radius of a search, in kilometers. */
export const DEFAULT_RADIUS_KM = 3

/**
 * The markets around a center. The center that leaves the device (and keys the cache) is always
 * rounded to 2 decimals, whatever position it came from, so a precise point is never sent.
 */
export function useMarketSearch(
  ws: string | undefined,
  center: GeoPoint | null,
  radiusKm: number = DEFAULT_RADIUS_KM,
) {
  const area: SearchArea | null =
    center === null ? null : { lat: roundTo(center.lat, 2), lon: roundTo(center.lon, 2), radiusKm }
  return useQuery({
    queryKey: ws !== undefined && area !== null ? marketsKeys.search(ws, area) : ['markets-off'],
    queryFn: () => searchMarkets(ws as string, area as SearchArea),
    enabled: ws !== undefined && area !== null,
    staleTime: 60 * 60 * 1000,
    // The key carries a region: it does not go to the copy kept on the device.
    meta: { persist: false },
  })
}

/** One market's card. */
export function useMarketCard(ws: string | undefined, ref: string | undefined) {
  return useQuery({
    queryKey: marketsKeys.card(ws ?? '', ref ?? ''),
    queryFn: () => getMarketCard(ws as string, ref as string),
    enabled: ws !== undefined && ref !== undefined,
  })
}

/** The family's address, or null without one. Only an Owner or an Adult may ask (`enabled`). */
export function useMarketHome(ws: string | undefined, enabled: boolean = true) {
  return useQuery({
    queryKey: marketsKeys.home(ws ?? ''),
    queryFn: () => getMarketHome(ws as string),
    enabled: ws !== undefined && enabled,
  })
}

/** Whether the address lookup and the Instagram embed are on. */
export function useMarketsSettings(ws: string | undefined) {
  return useQuery({
    queryKey: marketsKeys.settings(ws ?? ''),
    queryFn: () => getMarketsSettings(ws as string),
    enabled: ws !== undefined,
  })
}

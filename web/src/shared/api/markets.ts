/**
 * What only the Feira (markets) screens need: the search by area, a market's card, the family's
 * address and the module's switches. The `shared/api` barrel does not re-export this file, so none
 * of it weighs on the initial load. Every call needs the connection and none goes through the
 * offline queue (ADR-0007).
 */
import { client, unwrap } from './client'
import { queryKeys } from './queryKeys'
import type { components } from './schema'

type Schemas = components['schemas']

export type MarketSearchResult = Schemas['SearchResultView']
export type OsmMarketItem = Schemas['OsmItem']
export type FamilyMarketItem = Schemas['FamilyItem']
export type MarketCard = Schemas['MarketCardView']
export type MarketKind = Schemas['OsmItem']['kind']
export type MarketHome = Schemas['HomeView']
export type MarketsSettings = Schemas['SettingsView']
export type GeocodedPlace = Schemas['PlaceView']

/** The area a search sends: the center is always rounded to 2 decimals, so it never reveals a precise spot. */
export type SearchArea = { lat: string; lon: string; radiusKm: number }

/** Everything the Feira reads sits under the family key, so one invalidation of `all` reaches only it. */
export const marketsKeys = {
  all: (ws: string) => [...queryKeys.workspace(ws), 'markets'] as const,
  search: (ws: string, area: SearchArea) =>
    [...marketsKeys.all(ws), 'search', area.lat, area.lon, area.radiusKm] as const,
  card: (ws: string, ref: string) => [...marketsKeys.all(ws), 'card', ref] as const,
  home: (ws: string) => [...marketsKeys.all(ws), 'home'] as const,
  settings: (ws: string) => [...marketsKeys.all(ws), 'settings'] as const,
}

/** The markets around a center, as two separate lists: OpenStreetMap's and the family's own. */
export function searchMarkets(ws: string, area: SearchArea): Promise<MarketSearchResult> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/markets/search', {
      params: { path: { ws } },
      body: { lat: area.lat, lon: area.lon, radiusKm: area.radiusKm },
    }),
  )
}

/** One market's card. */
export function getMarketCard(ws: string, ref: string): Promise<MarketCard> {
  return unwrap(client.GET('/v1/workspaces/{ws}/markets/{ref}', { params: { path: { ws, ref } } }))
}

/** The family's address (a point and a label), or null while it has none. */
export async function getMarketHome(ws: string): Promise<MarketHome | null> {
  const home = await unwrap(
    client.GET('/v1/workspaces/{ws}/markets/home', { params: { path: { ws } } }),
  )
  return home ?? null
}

/** Which of the module's outside services are on. */
export function getMarketsSettings(ws: string): Promise<MarketsSettings> {
  return unwrap(client.GET('/v1/workspaces/{ws}/markets/settings', { params: { path: { ws } } }))
}

/** Looks an address up, on the person's request only: never while they type. */
export async function geocodeAddress(ws: string, query: string): Promise<GeocodedPlace[]> {
  const result = await unwrap(
    client.POST('/v1/workspaces/{ws}/markets/geocode', {
      params: { path: { ws } },
      body: { query },
    }),
  )
  return result.places
}

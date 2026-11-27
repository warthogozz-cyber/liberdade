/**
 * What only the Feira (markets) screens need: the search by area, a market's card, the family's
 * address and the module's switches. The `shared/api` barrel does not re-export this file, so none
 * of it weighs on the initial load. Every call needs the connection and none goes through the
 * offline queue (ADR-0007).
 */
import { useQuery } from '@tanstack/react-query'
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
export type ReceiptLookup = Schemas['LookupView']
export type ReceiptLookupBody = Schemas['LookupRequest']
export type SaveReceiptBody = Schemas['SaveReceiptRequest']
export type SaveReceiptItem = Schemas['ReceiptItem']
export type SavedReceipt = Schemas['SaveView']
export type MarketPrice = Schemas['PriceView']
export type FamilyProduct = Schemas['FamilyProductView']
export type ProductOverview = Schemas['ProductOverview']
export type ProductsOverview = Schemas['ProductsOverview']
export type SuggestedPair = Schemas['SuggestedPair']
export type EditProductBody = Schemas['EditProductRequest']

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
  prices: (ws: string, ref: string) => [...marketsKeys.all(ws), 'prices', ref] as const,
  products: (ws: string, q: string) => [...marketsKeys.all(ws), 'products', q] as const,
  // Under `products`, so a product change reaches it, yet never equal to `products(ws, q)`: an object is not a text.
  productsOverview: (ws: string) =>
    [...marketsKeys.all(ws), 'products', { view: 'overview' }] as const,
  // Also under `products`, for the same reason as the overview.
  productSuggestions: (ws: string) =>
    [...marketsKeys.all(ws), 'products', { view: 'suggestions' }] as const,
  list: (ws: string) => [...marketsKeys.all(ws), 'list'] as const,
  compare: (ws: string, refs: readonly string[]) =>
    [...marketsKeys.all(ws), 'compare', refs] as const,
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

/** Checks an NFC-e key against the API (nothing is saved): its market, when the CNPJ is already one. */
export function lookupReceipt(ws: string, body: ReceiptLookupBody): Promise<ReceiptLookup> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/markets/receipts/lookup', { params: { path: { ws } }, body }),
  )
}

/** Saves a receipt's prices in one transaction. */
export function saveReceipt(ws: string, body: SaveReceiptBody): Promise<SavedReceipt> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/markets/receipts', { params: { path: { ws } }, body }),
  )
}

/** The last price of each product at one market. */
export async function getMarketPrices(ws: string, ref: string): Promise<MarketPrice[]> {
  const view = await unwrap(
    client.GET('/v1/workspaces/{ws}/markets/{ref}/prices', { params: { path: { ws, ref } } }),
  )
  return view.prices
}

/** The family's products whose name contains `q` (all of them, up to the API's limit, when empty). */
export async function getFamilyProducts(ws: string, q: string): Promise<FamilyProduct[]> {
  const view = await unwrap(
    client.GET('/v1/workspaces/{ws}/markets/products', {
      params: { path: { ws }, query: q === '' ? {} : { q } },
    }),
  )
  return view.products
}

/** Every product of the family with its last price at each market (up to 500 products). */
export function getProductsOverview(ws: string): Promise<ProductsOverview> {
  return unwrap(
    client.GET('/v1/workspaces/{ws}/markets/products/overview', { params: { path: { ws } } }),
  )
}

/** The pairs of products that may be the same one (up to 20, the most alike first). */
export async function getProductSuggestions(ws: string): Promise<SuggestedPair[]> {
  const view = await unwrap(
    client.GET('/v1/workspaces/{ws}/markets/products/suggestions', { params: { path: { ws } } }),
  )
  return view.pairs
}

/** Renames a product or changes its unit; a `null` field stays as it is. */
export function editProduct(
  ws: string,
  id: string,
  body: EditProductBody,
): Promise<ProductOverview> {
  return unwrap(
    client.PATCH('/v1/workspaces/{ws}/markets/products/{id}', {
      params: { path: { ws, id } },
      body,
    }),
  )
}

/** Joins `removeId` into `keepId`: its prices, aliases and list item pass over, and it is gone. */
export function mergeProducts(
  ws: string,
  keepId: string,
  removeId: string,
): Promise<ProductOverview> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/markets/products/{keepId}/merge', {
      params: { path: { ws, keepId } },
      body: { removeId },
    }),
  )
}

/** Says that two products are not the same one, so they are not suggested again. */
export async function dismissPair(ws: string, a: string, b: string): Promise<void> {
  await unwrap(
    client.POST('/v1/workspaces/{ws}/markets/products/dismissals', {
      params: { path: { ws } },
      body: { a, b },
    }),
  )
}

/** The fields of a family market's links: `null` clears the family's value (the OSM one shows again). */
export type MarketLinksBody = Schemas['LinksRequest']
export type NewMarketBody = Schemas['NewMarketRequest']
export type MarketPoint = { lat: string; lon: string }

/** Saves a market's links; the answer is the updated card. */
export function putMarketLinks(
  ws: string,
  ref: string,
  body: MarketLinksBody,
): Promise<MarketCard> {
  return unwrap(
    client.PUT('/v1/workspaces/{ws}/markets/{ref}/links', {
      params: { path: { ws, ref } },
      body,
    }),
  )
}

/** Adds a market of the family's own. */
export function createMarket(ws: string, body: NewMarketBody): Promise<MarketCard> {
  return unwrap(client.POST('/v1/workspaces/{ws}/markets', { params: { path: { ws } }, body }))
}

/** Gives a family market its position. */
export function putMarketPosition(ws: string, ref: string, body: MarketPoint): Promise<MarketCard> {
  return unwrap(
    client.PUT('/v1/workspaces/{ws}/markets/{ref}/position', {
      params: { path: { ws, ref } },
      body,
    }),
  )
}

/** Hides a market from the lists. */
export async function archiveMarket(ws: string, ref: string): Promise<void> {
  await unwrap(
    client.POST('/v1/workspaces/{ws}/markets/{ref}/archive', { params: { path: { ws, ref } } }),
  )
}

/** Turns a market that left OpenStreetMap into one of the family's own, from what the person typed. */
export function convertMarket(ws: string, ref: string, body: NewMarketBody): Promise<MarketCard> {
  return unwrap(
    client.POST('/v1/workspaces/{ws}/markets/{ref}/convert', {
      params: { path: { ws, ref } },
      body,
    }),
  )
}

/** Saves the family's address: a point of 3 decimals at most and an optional label. */
export function putMarketHome(
  ws: string,
  body: { lat: string; lon: string; label: string | null },
): Promise<MarketHome> {
  return unwrap(client.PUT('/v1/workspaces/{ws}/markets/home', { params: { path: { ws } }, body }))
}

/** Erases the family's address. */
export async function deleteMarketHome(ws: string): Promise<void> {
  await unwrap(client.DELETE('/v1/workspaces/{ws}/markets/home', { params: { path: { ws } } }))
}

/** The Instagram embed's markup for a market, or an `ApiError` (422) when Meta does not give it. */
export async function getInstagramEmbed(ws: string, ref: string): Promise<string> {
  const view = await unwrap(
    client.POST('/v1/workspaces/{ws}/markets/{ref}/instagram-embed', {
      params: { path: { ws, ref } },
    }),
  )
  return view.html
}

/** The family's address, or null without one. Only an Owner or an Adult may ask (`enabled`). */
export function useMarketHome(ws: string | undefined, enabled: boolean = true) {
  return useQuery({
    queryKey: marketsKeys.home(ws ?? ''),
    queryFn: () => getMarketHome(ws as string),
    enabled: ws !== undefined && enabled,
    // The home point is about 100 m: it stays out of the copy kept on the device.
    meta: { persist: false },
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

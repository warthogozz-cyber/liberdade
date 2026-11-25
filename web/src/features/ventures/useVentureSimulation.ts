import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import {
  simulateVenture,
  venturesKeys,
  type SimulationRequest,
  type VentureFigures,
  type VentureType,
} from '@/shared/api/ventures'
import { centsToApi, parseTypedAmount } from '@/shared/money'
import { useOnline } from '@/shared/offline'

/** How long the draft must stay still before the figures are asked for. */
export const SIMULATION_DELAY_MS = 400
/** Planned sales go from 0 to a million a month (Global Constraints). */
export const MAX_PLANNED_UNITS = 1_000_000

/** A cost row as the person typed it. `key` identifies the row on screen; `id` is the saved item's. */
export interface DraftCost {
  key: string
  id: string | null
  name: string
  amount: string
  isExample: boolean
}

export interface DraftProduct {
  key: string
  id: string | null
  name: string
  unitPrice: string
  unitVariableCost: string
  plannedUnits: string
  isExample: boolean
}

/** The whole project as typed: texts, not numbers, so a half-typed value stays as it is. */
export interface VentureDraft {
  name: string
  type: VentureType
  startedOn: string | null
  initialItems: DraftCost[]
  fixedItems: DraftCost[]
  products: DraftProduct[]
}

/** A typed amount in cents: a positive one, or zero, which `parseTypedAmount` leaves out. */
export function parseAmount(text: string): bigint | undefined {
  const clean = text.trim()
  if (/^0+(,0{1,2})?$/.test(clean)) return 0n
  return parseTypedAmount(clean)
}

/** Planned units a month as a whole number from 0 to a million, or undefined. */
export function parseUnits(text: string): number | undefined {
  const clean = text.trim()
  if (!/^\d{1,7}$/.test(clean)) return undefined
  const units = Number(clean)
  return units <= MAX_PLANNED_UNITS ? units : undefined
}

function apiAmount(text: string): string | undefined {
  const cents = parseAmount(text)
  return cents === undefined ? undefined : centsToApi(cents)
}

/**
 * The simulation request for a draft: names are left out (they never change the figures) and so is
 * every row whose number does not read yet, so a half-typed value does not break the preview.
 */
export function buildSimulationRequest(draft: VentureDraft): SimulationRequest {
  const costs = (rows: readonly DraftCost[]) =>
    rows.flatMap((row) => {
      const amount = apiAmount(row.amount)
      return amount === undefined ? [] : [{ amount }]
    })
  return {
    initialItems: costs(draft.initialItems),
    fixedItems: costs(draft.fixedItems),
    products: draft.products.flatMap((product) => {
      const unitPrice = apiAmount(product.unitPrice)
      const unitVariableCost = apiAmount(product.unitVariableCost)
      const plannedUnitsMonth = parseUnits(product.plannedUnits)
      return unitPrice === undefined ||
        unitVariableCost === undefined ||
        plannedUnitsMonth === undefined
        ? []
        : [{ unitPrice, unitVariableCost, plannedUnitsMonth }]
    }),
  }
}

/**
 * The live figures of the draft. It asks the API 400 ms after the last change, the last request
 * wins and the previous figures stay on screen meanwhile. Offline it asks nothing: the screen says
 * to connect.
 */
export function useVentureSimulation(
  ws: string,
  draft: VentureDraft,
): { figures?: VentureFigures; offline: boolean; pending: boolean } {
  const online = useOnline()
  const request = buildSimulationRequest(draft)
  const key = JSON.stringify(request)
  const [settled, setSettled] = useState({ key, request })

  useEffect(() => {
    if (key === settled.key) return
    const timer = setTimeout(
      () => setSettled({ key, request: JSON.parse(key) as SimulationRequest }),
      SIMULATION_DELAY_MS,
    )
    return () => clearTimeout(timer)
  }, [key, settled.key])

  const query = useQuery({
    queryKey: venturesKeys.simulation(ws, settled.request),
    queryFn: () => simulateVenture(ws, settled.request),
    enabled: online && ws !== '',
    placeholderData: keepPreviousData,
    meta: { persist: false },
  })

  return {
    ...(query.data !== undefined ? { figures: query.data } : {}),
    offline: !online,
    pending: key !== settled.key || query.isFetching,
  }
}

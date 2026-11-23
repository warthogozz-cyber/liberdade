/**
 * The paid plan: the family's plan, the checkout, the order the person comes back to, the refund and
 * the Founder theme. Only the Plan screen and the account-deletion sheet use them, so the
 * `shared/api` barrel does not re-export this file.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { client, unwrap } from './client'
import { queryKeys } from './queryKeys'
import type { components } from './schema'

export type PlanDetails = components['schemas']['PlanDetails']
export type OrderView = components['schemas']['OrderView']
export type CheckoutView = components['schemas']['CheckoutView']
export type FounderTheme = components['schemas']['ThemeRequest']['theme']

/** How often the open order is asked about, and how many times (2 minutes in all). */
export const REFRESH_INTERVAL_MS = 5_000
export const REFRESH_MAX_CALLS = 24

const planKey = (ws: string) => [...queryKeys.workspace(ws), 'billing', 'plan'] as const
const orderKey = (ws: string, orderId: string) =>
  [...queryKeys.workspace(ws), 'billing', 'order', orderId] as const

function callsMade(state: { dataUpdateCount: number; errorUpdateCount: number } | undefined) {
  return state ? state.dataUpdateCount + state.errorUpdateCount : 0
}

/** The family's plan; `ws` is undefined until the family is known. */
export function usePlan(ws: string | undefined) {
  return useQuery({
    queryKey: planKey(ws ?? ''),
    queryFn: () =>
      unwrap(client.GET('/v1/workspaces/{ws}/billing/plan', { params: { path: { ws: ws! } } })),
    enabled: ws !== undefined,
  })
}

/** Starts the checkout and sends the person to the payment page. */
export function useStartCheckout(
  ws: string,
  assign: (url: string) => void = (url) => window.location.assign(url),
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      unwrap(client.POST('/v1/workspaces/{ws}/billing/checkout', { params: { path: { ws } } })),
    onSuccess: (checkout) => assign(checkout.checkoutUrl),
    onSettled: () => queryClient.invalidateQueries({ queryKey: planKey(ws) }),
  })
}

/**
 * Asks the API about an order every 5 seconds while it waits for payment, up to 24 times. It stops
 * when the order leaves CREATED, after the 24th call, or when the screen closes; when the order
 * leaves CREATED the plan is read again.
 */
export function useRefreshOrder(ws: string, orderId: string | null) {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: orderKey(ws, orderId ?? ''),
    queryFn: () =>
      unwrap(
        client.POST('/v1/workspaces/{ws}/billing/orders/{id}/refresh', {
          params: { path: { ws, id: orderId! } },
        }),
      ),
    enabled: orderId !== null,
    staleTime: 0,
    // Opening the screen again starts a new count.
    gcTime: 0,
    meta: { persist: false },
    retry: false,
    // Coming back from the payment tab must not add calls: only the timer asks.
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    refetchInterval: (current) => {
      const done = current.state.data !== undefined && current.state.data.status !== 'CREATED'
      return done || callsMade(current.state) >= REFRESH_MAX_CALLS ? false : REFRESH_INTERVAL_MS
    },
  })
  // Read for their effect: each answer re-renders, even an unchanged one, so the count is current.
  void query.dataUpdatedAt
  void query.errorUpdatedAt
  const status = query.data?.status
  // All the calls were made and the order still waits: polling has given up.
  const exhausted =
    orderId !== null &&
    (status === undefined || status === 'CREATED') &&
    callsMade(queryClient.getQueryState(orderKey(ws, orderId))) >= REFRESH_MAX_CALLS
  useEffect(() => {
    if ((status !== undefined && status !== 'CREATED') || exhausted) {
      void queryClient.invalidateQueries({ queryKey: planKey(ws) })
    }
  }, [status, exhausted, queryClient, ws])
  return { status, exhausted }
}

/** Asks for the money back for the paid order (`paidOrderId`). */
export function useRequestRefund(ws: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (orderId: string) =>
      unwrap(
        client.POST('/v1/workspaces/{ws}/billing/orders/{id}/refund', {
          params: { path: { ws, id: orderId } },
        }),
      ),
    onSettled: () => queryClient.invalidateQueries({ queryKey: planKey(ws) }),
  })
}

export function useSetTheme(ws: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (theme: FounderTheme) =>
      unwrap(
        client.PUT('/v1/workspaces/{ws}/billing/plan/theme', {
          params: { path: { ws } },
          body: { theme },
        }),
      ),
    onSettled: () => queryClient.invalidateQueries({ queryKey: planKey(ws) }),
  })
}

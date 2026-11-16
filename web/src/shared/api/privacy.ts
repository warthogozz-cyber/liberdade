/**
 * Privacy and the modules' switches: activity, consents, exports, deletion and `PUT …/modules/{key}`.
 * Only lazy screens use them, so the `shared/api` barrel does not re-export this file.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { client, unwrap } from './client'
import { API_URL } from './config'
import { csrfFetch } from './csrf'
import { ApiError, errorFromResponse } from './errors'
import { queryKeys } from './queryKeys'
import type { ModuleKey } from './types'
import type { components } from './schema'

export type ActivityItem = components['schemas']['ActivityItem']
export type ConsentView = components['schemas']['ConsentView']

const activityKey = ['me', 'activity'] as const
const consentsKey = ['me', 'consents'] as const

export function listActivity(): Promise<ActivityItem[]> {
  return unwrap(client.GET('/v1/me/activity'))
}

export function listConsents(): Promise<ConsentView[]> {
  return unwrap(client.GET('/v1/me/consents'))
}

/** Takes a consent back; a Youth it covers leaves the family. Needs a recent sign-in. */
export async function revokeConsent(id: string): Promise<void> {
  await unwrap(client.DELETE('/v1/me/consents/{id}', { params: { path: { id } } }))
}

/** Schedules the account's deletion in 30 days. A sole Owner is a 409 `account.sole-owner`. */
export function requestAccountDeletion(): Promise<{ deletionScheduledFor: string }> {
  return unwrap(client.POST('/v1/me/deletion'))
}

export async function cancelAccountDeletion(): Promise<void> {
  await unwrap(client.DELETE('/v1/me/deletion'))
}

export function requestFamilyDeletion(ws: string): Promise<{ deletionScheduledFor: string }> {
  return unwrap(client.POST('/v1/workspaces/{ws}/deletion', { params: { path: { ws } } }))
}

export async function cancelFamilyDeletion(ws: string): Promise<void> {
  await unwrap(client.DELETE('/v1/workspaces/{ws}/deletion', { params: { path: { ws } } }))
}

/** Switches a module on or off. */
export function setModuleEnabled(ws: string, key: ModuleKey, enabled: boolean) {
  return unwrap(
    client.PUT('/v1/workspaces/{ws}/modules/{key}', {
      params: { path: { ws, key } },
      body: { enabled },
    }),
  )
}

/**
 * POSTs to an export route and returns the zip with the name the API gave it. A failed answer
 * throws its ApiError (`ApiError` status 0 when there was no answer).
 */
export async function postExport(path: string): Promise<{ blob: Blob; disposition: string | null }> {
  let response: Response
  try {
    response = await csrfFetch(new Request(`${API_URL}${path}`, { method: 'POST', credentials: 'include' }))
  } catch {
    throw new ApiError(0, 'network')
  }
  if (!response.ok) throw await errorFromResponse(response)
  return { blob: await response.blob(), disposition: response.headers.get('Content-Disposition') }
}

export function useActivity() {
  // Personal history: kept in memory only, never saved to the device.
  return useQuery({ queryKey: activityKey, queryFn: listActivity, meta: { persist: false } })
}

export function useConsents() {
  return useQuery({ queryKey: consentsKey, queryFn: listConsents, meta: { persist: false } })
}

/** Revokes a consent, then refreshes the consents, the family's people and the signed-in person's data. */
export function useRevokeConsent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => revokeConsent(id),
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: consentsKey }),
        queryClient.invalidateQueries({ queryKey: activityKey }),
        queryClient.invalidateQueries({ queryKey: queryKeys.me }),
        queryClient.invalidateQueries({ queryKey: ['ws'] }),
      ]),
  })
}

/** Schedules or cancels the account's deletion; `me` shows the result. */
export function useAccountDeletion() {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.me })
  return {
    request: useMutation({ mutationFn: requestAccountDeletion, onSuccess: refresh }),
    cancel: useMutation({ mutationFn: cancelAccountDeletion, onSuccess: refresh }),
  }
}

/** Schedules or cancels a family's deletion; `me` shows the result. */
export function useFamilyDeletion(ws: string) {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.me })
  return {
    request: useMutation({ mutationFn: () => requestFamilyDeletion(ws), onSuccess: refresh }),
    cancel: useMutation({ mutationFn: () => cancelFamilyDeletion(ws), onSuccess: refresh }),
  }
}

/** Switches a module; the modules list follows the answer. */
export function useSetModule(ws: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ key, enabled }: { key: ModuleKey; enabled: boolean }) =>
      setModuleEnabled(ws, key, enabled),
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.modules(ws) }),
  })
}

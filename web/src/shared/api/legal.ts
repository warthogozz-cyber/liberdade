/**
 * The versions of the terms and of the privacy policy in force, public and without a session. Only
 * the legal pages use them, so the `shared/api` barrel does not re-export this file.
 */
import { useQuery } from '@tanstack/react-query'
import { client, unwrap } from './client'
import type { components } from './schema'

export type LegalVersions = components['schemas']['LegalVersions']

const legalVersionsKey = ['legal', 'current'] as const

export function getLegalVersions(): Promise<LegalVersions> {
  return unwrap(client.GET('/v1/legal/current'))
}

/** Kept on the device, so offline the pages show the last versions the API named. */
export function useLegalVersions() {
  return useQuery({
    queryKey: legalVersionsKey,
    queryFn: getLegalVersions,
    meta: { persist: true },
  })
}

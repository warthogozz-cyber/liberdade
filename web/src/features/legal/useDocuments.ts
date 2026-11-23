import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { acceptDocuments, listDocuments, queryKeys, type DocumentStatus } from '@/shared/api'

export interface Documents {
  /**
   * The documents still to accept. `undefined` while the answer is loading, without a connection or
   * on an error: only a real answer can put the gate up, so offline never locks the family out (D13).
   */
  pending: DocumentStatus[] | undefined
  /**
   * The version of the privacy policy that came out since the person accepted, when what they accepted
   * still counts (no new accept needed). `undefined` otherwise.
   */
  policyUpdated: string | undefined
  /** Accepts the versions the API listed as current. A 409 reloads the list before it throws. */
  accept(): Promise<void>
}

/**
 * What to send to accept the terms and the policy: the `currentVersion` the API listed for each, so
 * the person accepts what is in force where they are, never a version fixed in the build. A list
 * without one of them throws before anything is posted, so the cause is plain.
 */
export function versionsToAccept(documents: DocumentStatus[]): { terms: string; privacy: string } {
  const current = (purpose: DocumentStatus['purpose']) => {
    const version = documents.find((document) => document.purpose === purpose)?.currentVersion
    if (!version) throw new Error(`documents: no current version for ${purpose}`)
    return version
  }
  return { terms: current('TERMS'), privacy: current('PRIVACY') }
}

/**
 * The documents that wait for an accept: those the API says need one (`acceptanceRequired`). A
 * version that differs from the accepted one, but keeps its acceptance, does not block.
 */
export function documentsToAccept(documents: DocumentStatus[]): DocumentStatus[] {
  return documents.filter(
    (document) => document.purpose !== 'PARENTAL' && document.acceptanceRequired,
  )
}

/** The current policy version when the person accepted an older one that still counts, else `undefined`. */
export function policyUpdate(documents: DocumentStatus[]): string | undefined {
  const policy = documents.find((document) => document.purpose === 'PRIVACY')
  if (!policy || policy.acceptanceRequired) return undefined
  return policy.acceptedVersion !== policy.currentVersion ? policy.currentVersion : undefined
}

/** The person's terms and privacy policy, and what is left to accept. */
export function useDocuments(): Documents {
  const queryClient = useQueryClient()
  // Personal and short-lived: never kept on the device, so a stale "pending" cannot come back.
  const query = useQuery({
    queryKey: queryKeys.documents,
    queryFn: listDocuments,
    meta: { persist: false },
  })
  const mutation = useMutation({
    mutationFn: (documents: DocumentStatus[]) => acceptDocuments(versionsToAccept(documents)),
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.documents }),
  })
  return {
    pending: query.data && documentsToAccept(query.data),
    policyUpdated: query.data && policyUpdate(query.data),
    // `onSettled` reloads the list on success and on a 409 alike. The gate only shows with a list.
    accept: () => mutation.mutateAsync(query.data ?? []),
  }
}

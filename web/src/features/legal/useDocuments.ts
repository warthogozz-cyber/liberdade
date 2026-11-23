import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { acceptDocuments, listDocuments, queryKeys, type DocumentStatus } from '@/shared/api'

export interface Documents {
  /**
   * The documents still to accept. `undefined` while the answer is loading, without a connection or
   * on an error: only a real answer can put the gate up, so offline never locks the family out (D13).
   */
  pending: DocumentStatus[] | undefined
  /** Accepts the versions the API listed as current. A 409 reloads the list before it throws. */
  accept(): Promise<void>
}

/**
 * What to send to accept the terms and the policy: the `currentVersion` the API listed for each, so
 * the person accepts what is in force where they are, never a version fixed in the build.
 */
export function versionsToAccept(documents: DocumentStatus[]): { terms: string; privacy: string } {
  const current = (purpose: DocumentStatus['purpose']) =>
    documents.find((document) => document.purpose === purpose)?.currentVersion ?? ''
  return { terms: current('TERMS'), privacy: current('PRIVACY') }
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
    pending: query.data?.filter(
      (document) =>
        document.purpose !== 'PARENTAL' && document.acceptedVersion !== document.currentVersion,
    ),
    // `onSettled` reloads the list on success and on a 409 alike. The gate only shows with a list.
    accept: () => mutation.mutateAsync(query.data ?? []),
  }
}

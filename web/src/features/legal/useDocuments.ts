import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { acceptDocuments, listDocuments, queryKeys, type DocumentStatus } from '@/shared/api'
import { legalConfig } from './legalConfig'

export interface Documents {
  /**
   * The documents still to accept. `undefined` while the answer is loading, without a connection or
   * on an error: only a real answer can put the gate up, so offline never locks the family out (D13).
   */
  pending: DocumentStatus[] | undefined
  /** True from the first request until it answers or fails once. */
  loading: boolean
  /** Accepts the versions this build shows. A 409 reloads the list before it throws. */
  accept(): Promise<void>
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
    mutationFn: () =>
      acceptDocuments({
        terms: legalConfig.versions.terms,
        privacy: legalConfig.versions.privacy,
      }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.documents }),
  })
  return {
    pending: query.data?.filter(
      (document) =>
        document.purpose !== 'PARENTAL' && document.acceptedVersion !== document.currentVersion,
    ),
    // Once a request has failed, the person is let in while the retries go on.
    loading: query.isPending && query.fetchStatus === 'fetching' && query.failureCount === 0,
    // `onSettled` reloads the list on success and on a 409 alike.
    accept: () => mutation.mutateAsync(),
  }
}

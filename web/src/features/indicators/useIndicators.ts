import { useQuery } from '@tanstack/react-query'
import { client, unwrap } from '@/shared/api/client'
import { queryKeys } from '@/shared/api/queryKeys'

const THIRTY_MINUTES = 30 * 60 * 1000

/** The Banco Central indicators, read from our API (the browser never calls the bank). */
export function useIndicators(workspaceId: string | undefined) {
  const ws = workspaceId ?? ''
  return useQuery({
    queryKey: [...queryKeys.workspace(ws), 'indicators'] as const,
    queryFn: () =>
      unwrap(client.GET('/v1/workspaces/{ws}/indicators', { params: { path: { ws } } })),
    enabled: ws !== '',
    staleTime: THIRTY_MINUTES,
  })
}

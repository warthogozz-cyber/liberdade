import { useQuery } from '@tanstack/react-query'
import { getComparison, marketsKeys } from '@/shared/api/markets'

/**
 * The shopping list priced at the chosen markets, in the order given. With no markets it is only the
 * two suggested ones. One market is no comparison (the API refuses it), and neither are four.
 */
export function useCompare(ws: string | undefined, refs: readonly string[]) {
  return useQuery({
    queryKey: marketsKeys.compare(ws ?? '', refs),
    queryFn: () => getComparison(ws as string, refs),
    enabled: ws !== undefined && refs.length !== 1 && refs.length <= 3,
  })
}

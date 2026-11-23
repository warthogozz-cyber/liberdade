import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { archiveTag, listTags, renameTag } from './endpoints'
import { queryKeys } from './queryKeys'

/** The family's active tags, which the tag field suggests and the filters list; waits for a family id. */
export function useTags(ws: string) {
  return useQuery({
    queryKey: queryKeys.tags(ws),
    queryFn: () => listTags(ws),
    enabled: ws !== '',
  })
}

/** Renaming or archiving a tag changes the transactions that carry it, so the family refreshes. */
function useRefreshWorkspace(ws: string) {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: queryKeys.workspace(ws) })
}

/** Renames a tag; its transactions show the new name. */
export function useRenameTag(ws: string) {
  return useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) => renameTag(ws, id, name),
    onSettled: useRefreshWorkspace(ws),
  })
}

/** Archives a tag; the transactions that have it keep it. */
export function useArchiveTag(ws: string) {
  return useMutation({
    mutationFn: (id: string) => archiveTag(ws, id),
    onSettled: useRefreshWorkspace(ws),
  })
}

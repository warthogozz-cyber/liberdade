import { useLiveSync } from './useLiveSync'

/** Keeps the family's data current while mounted; draws nothing. */
export function LiveSync({ workspaceId }: { workspaceId: string }) {
  useLiveSync(workspaceId)
  return null
}

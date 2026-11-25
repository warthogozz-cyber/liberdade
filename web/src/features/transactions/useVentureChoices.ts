import { useCurrentWorkspace } from '@/shared/api'
import { useVentures, type VentureSummary } from '@/shared/api/ventures'
import { useModuleEnabled } from '@/shared/modules'

/**
 * The projects a transaction can be marked with, or null when the field does not show: the module
 * is off, the person is a Youth, or no project is open. A project outside `CLOSED` is offered, and
 * so is `keep` (the one a transaction already has), even once it is closed. The list is kept on the
 * device, so this works offline. Reads only `shared/api/ventures`, so the form stays small.
 */
export function useVentureChoices(keep: string | null): VentureSummary[] | null {
  const workspace = useCurrentWorkspace()
  const enabled = useModuleEnabled('ventures')
  const allowed = enabled && workspace !== undefined && workspace.role !== 'YOUTH'
  const ventures = useVentures(allowed ? workspace.id : '').data?.ventures
  if (!allowed || !ventures) return null
  const choices = ventures.filter(({ status, id }) => status !== 'CLOSED' || id === keep)
  return choices.length > 0 ? choices : null
}

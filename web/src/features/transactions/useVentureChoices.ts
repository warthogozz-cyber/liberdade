import { useCurrentWorkspace, useModules } from '@/shared/api'
import { useVentures, type VentureSummary } from '@/shared/api/ventures'

export interface VentureChoices {
  /** The projects on offer, or null when the field does not show. */
  choices: VentureSummary[] | null
  /** Whether the module and the list have answered (or cannot matter), so null really means none. */
  known: boolean
}

/**
 * The projects a transaction can be marked with. `choices` is null when the field does not show:
 * the module is off, the person is a Youth, or no project is open. A project outside `CLOSED` is
 * offered, and so is `keep` (the one a transaction already has), even once it is closed. The list
 * is kept on the device, so this works offline. Reads only `shared/api`, so the form stays small.
 */
export function useVentureChoices(keep: string | null): VentureChoices {
  const workspace = useCurrentWorkspace()
  const modules = useModules(workspace?.id).data
  const enabled = modules?.find((module) => module.key === 'ventures')?.enabled === true
  const allowed = enabled && workspace !== undefined && workspace.role !== 'YOUTH'
  const ventures = useVentures(allowed ? workspace.id : '').data?.ventures
  const known = modules !== undefined && (!allowed || ventures !== undefined)
  if (!allowed || !ventures) return { choices: null, known }
  const choices = ventures.filter(({ status, id }) => status !== 'CLOSED' || id === keep)
  return { choices: choices.length > 0 ? choices : null, known }
}

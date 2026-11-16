import { useCurrentWorkspace, useModules, type ModuleKey } from '@/shared/api'

/**
 * Whether the family switched the module on. False while the list loads, and offline when there is
 * no copy of it.
 */
export function useModuleEnabled(key: ModuleKey): boolean {
  const workspace = useCurrentWorkspace()
  const modules = useModules(workspace?.id).data
  return modules?.find((module) => module.key === key)?.enabled === true
}

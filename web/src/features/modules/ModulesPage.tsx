import { useState } from 'react'
import { useIntl } from 'react-intl'
import {
  ApiError,
  useCurrentWorkspace,
  useModules,
  type MeWorkspace,
  type ModuleKey,
} from '@/shared/api'
import { useSetModule } from '@/shared/api/privacy'
import { useOnline } from '@/shared/offline'
import { useDocumentTitle } from '@/shared/ui'
import { isAvailable, OPTIONAL_MODULES } from './moduleCatalog'
import './register'

/** What the family can switch on beyond the core. Only an Owner or an Adult has the switches. */
export function ModulesPage() {
  const intl = useIntl()
  const title = intl.formatMessage({ id: 'modules.title' })
  useDocumentTitle(title)
  const workspace = useCurrentWorkspace()
  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {workspace ? (
        <ModuleList workspace={workspace} />
      ) : (
        <p>{intl.formatMessage({ id: 'common.loading' })}</p>
      )}
    </main>
  )
}

function ModuleList({ workspace }: { workspace: MeWorkspace }) {
  const intl = useIntl()
  const online = useOnline()
  const modules = useModules(workspace.id)
  const setModule = useSetModule(workspace.id)
  const [error, setError] = useState<string>()
  const canSwitch = workspace.role === 'OWNER' || workspace.role === 'ADULT'

  if (modules.isPending) return <p>{intl.formatMessage({ id: 'common.loading' })}</p>
  if (modules.isError) return <p role="alert">{intl.formatMessage({ id: 'modules.loadFailed' })}</p>

  async function toggle(key: ModuleKey, enabled: boolean) {
    setError(undefined)
    try {
      await setModule.mutateAsync({ key, enabled })
    } catch (failure) {
      setError(
        (failure instanceof ApiError && failure.detail) ||
          intl.formatMessage({ id: 'common.actionFailed' }),
      )
    }
  }

  return (
    <>
      {canSwitch && !online && (
        <p className="text-text-muted">{intl.formatMessage({ id: 'modules.offline' })}</p>
      )}
      <ul className="flex flex-col divide-y divide-border">
        <li className="flex items-center justify-between gap-3 py-3">
          <div>
            <p className="font-medium">{intl.formatMessage({ id: 'modules.core' })}</p>
            <p className="text-sm text-text-muted">
              {intl.formatMessage({ id: 'modules.core.description' })}
            </p>
          </div>
          <span className="text-sm text-text-muted">
            {intl.formatMessage({ id: 'modules.coreAlways' })}
          </span>
        </li>
        {OPTIONAL_MODULES.map((key) => {
          const name = intl.formatMessage({ id: `modules.${key}` as 'modules.planning' })
          const available = isAvailable(key)
          const on = modules.data.find((module) => module.key === key)?.enabled === true
          return (
            <li key={key} className="flex items-center justify-between gap-3 py-3">
              <div>
                <p className="font-medium">{name}</p>
                <p id={`module-${key}`} className="text-sm text-text-muted">
                  {intl.formatMessage({
                    id: `modules.${key}.description` as 'modules.planning.description',
                  })}
                </p>
              </div>
              {!available && (
                <span className="text-sm text-text-muted">
                  {intl.formatMessage({ id: 'modules.comingSoon' })}
                </span>
              )}
              {canSwitch && (
                <button
                  type="button"
                  role="switch"
                  aria-checked={on}
                  aria-label={name}
                  aria-describedby={`module-${key}`}
                  disabled={!available || !online || setModule.isPending}
                  onClick={() => void toggle(key, !on)}
                  className="flex min-h-12 min-w-12 items-center justify-center rounded-field border border-primary px-3 text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
                >
                  {on ? '●' : '○'}
                </button>
              )}
            </li>
          )
        })}
      </ul>
      {error && (
        <p role="alert" className="text-negative">
          {error}
        </p>
      )}
    </>
  )
}

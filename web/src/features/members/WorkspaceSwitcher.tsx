import { useIntl } from 'react-intl'
import { selectWorkspace, useCurrentWorkspace, useWorkspaces } from '@/shared/api'
import './register'

/** Lets a person who belongs to more than one family pick which one this device shows. */
export function WorkspaceSwitcher() {
  const intl = useIntl()
  const workspaces = useWorkspaces()
  const current = useCurrentWorkspace()
  if (workspaces.length < 2) return null

  return (
    <section aria-labelledby="workspace-switcher" className="flex flex-col gap-2">
      <h2 id="workspace-switcher" className="font-semibold">
        {intl.formatMessage({ id: 'members.switcher.title' })}
      </h2>
      <ul className="flex flex-col gap-2">
        {workspaces.map((workspace) => {
          const isCurrent = workspace.id === current?.id
          return (
            <li key={workspace.id}>
              <button
                type="button"
                aria-current={isCurrent ? 'true' : undefined}
                onClick={() => selectWorkspace(workspace.id)}
                className={`flex min-h-12 w-full items-center justify-between gap-2 rounded-field border px-4 text-left font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
                  isCurrent ? 'border-primary bg-surface' : 'border-border'
                }`}
              >
                <span>{workspace.name}</span>
                {isCurrent && (
                  <span className="text-sm text-text-muted">
                    {intl.formatMessage({ id: 'members.switcher.current' })}
                  </span>
                )}
              </button>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

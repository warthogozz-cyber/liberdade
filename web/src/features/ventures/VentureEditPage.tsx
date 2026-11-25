import { useState } from 'react'
import { useIntl } from 'react-intl'
import { useParams } from 'react-router'
import { useCurrentWorkspace } from '@/shared/api'
import { useVenture } from '@/shared/api/ventures'
import { registerMessages } from '@/shared/i18n'
import { useDocumentTitle } from '@/shared/ui'
import { venturesMessages } from './messages.pt-BR'
import { VentureForm } from './VentureForm'

registerMessages(venturesMessages)

/** Opens a project in its form. The form keeps what was typed until the person asks to reload. */
export function VentureEditPage() {
  const intl = useIntl()
  useDocumentTitle(intl.formatMessage({ id: 'ventures.edit.title' }))
  const { id = '' } = useParams()
  const workspace = useCurrentWorkspace()
  const venture = useVenture(workspace?.id ?? '', id)
  const [generation, setGeneration] = useState(0)

  if (workspace && venture.data) {
    return (
      <VentureForm
        key={`${venture.data.id}-${generation}`}
        workspace={workspace}
        venture={venture.data}
        onReload={() => {
          void venture.refetch().then(() => setGeneration((current) => current + 1))
        }}
      />
    )
  }
  return (
    <main className="mx-auto flex max-w-lg flex-col gap-4 p-6">
      {venture.isError ? (
        <>
          <p>{intl.formatMessage({ id: 'common.error' })}</p>
          <button
            type="button"
            onClick={() => void venture.refetch()}
            className="min-h-12 self-start rounded-field border border-primary px-4 font-medium text-primary"
          >
            {intl.formatMessage({ id: 'common.retry' })}
          </button>
        </>
      ) : (
        <div aria-busy="true" className="h-40 rounded-card bg-surface" />
      )}
    </main>
  )
}

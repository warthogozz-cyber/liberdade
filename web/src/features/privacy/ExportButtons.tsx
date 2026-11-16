import { useState } from 'react'
import { useIntl } from 'react-intl'
import { ApiError, ReauthCancelledError, type MeWorkspace } from '@/shared/api'
import { useReauth } from '@/shared/auth'
import { useOnline } from '@/shared/offline'
import { outlineButton } from './buttons'
import { downloadZip } from './download'
import './register'

interface ExportButtonProps {
  /** A family's id for the family's data; without it, the signed-in person's own. */
  workspaceId?: string
}

/** Downloads a zip of the person's own data, or of the whole family's. Needs a recent sign-in. */
export function ExportButton({ workspaceId }: ExportButtonProps) {
  const intl = useIntl()
  const online = useOnline()
  const run = useReauth()
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState<string>()

  async function download() {
    if (!online || busy) return
    setBusy(true)
    setFailed(undefined)
    try {
      await run(() =>
        workspaceId === undefined
          ? downloadZip('/v1/me/export', 'liberdade-meus-dados.zip')
          : downloadZip(`/v1/workspaces/${workspaceId}/export`, 'liberdade-familia.zip'),
      )
    } catch (error) {
      if (!(error instanceof ReauthCancelledError)) {
        setFailed(
          (error instanceof ApiError && error.detail) ||
            intl.formatMessage({ id: 'privacy.exportFailed' }),
        )
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-col gap-1">
      <button
        type="button"
        disabled={!online || busy}
        onClick={() => void download()}
        className={outlineButton}
      >
        {intl.formatMessage({
          id: workspaceId === undefined ? 'privacy.exportMine' : 'privacy.exportFamily',
        })}
      </button>
      {failed && (
        <p role="alert" className="text-sm text-negative">
          {failed}
        </p>
      )}
    </div>
  )
}

/** The person's own data, and, for an Owner, the family's. */
export function ExportButtons({ workspace }: { workspace?: Pick<MeWorkspace, 'id' | 'role'> }) {
  return (
    <>
      <ExportButton />
      {workspace?.role === 'OWNER' && <ExportButton workspaceId={workspace.id} />}
    </>
  )
}

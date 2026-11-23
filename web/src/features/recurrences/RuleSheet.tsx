import { useState } from 'react'
import { FormattedMessage, useIntl } from 'react-intl'
import {
  ApiError,
  type CreateRecurringRuleRequest,
  type MeWorkspace,
  type RecurringRuleView,
} from '@/shared/api'
import { useArchiveRule, useCreateRule, useUpdateRule } from '@/shared/api/recurrences'
import { useOnline } from '@/shared/offline'
import { useToast } from '@/shared/ui'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { Sheet } from '@/shared/ui/Sheet'
import { RuleForm } from './RuleForm'
import './register'

interface RuleSheetProps {
  workspace: MeWorkspace
  /** The rule to edit; none opens a new one. */
  rule?: RecurringRuleView
  onClose: () => void
}

/**
 * A rule to create, or to edit and archive. Every call needs the connection (nothing here goes
 * through the offline queue), so the buttons stay off without it and the page says why.
 */
export function RuleSheet({ workspace, rule, onClose }: RuleSheetProps) {
  const intl = useIntl()
  const toast = useToast()
  const online = useOnline()
  const create = useCreateRule(workspace.id)
  const update = useUpdateRule(workspace.id)
  const archive = useArchiveRule(workspace.id)
  const [confirming, setConfirming] = useState(false)
  const busy = create.isPending || update.isPending || archive.isPending

  /** A rule that changed or is gone elsewhere says so; the list is fetched again. */
  function failed(error: unknown) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 409)) {
      toast.show({ message: intl.formatMessage({ id: 'recurrences.changed' }) })
      onClose()
      return
    }
    console.error(error)
    toast.show({
      message:
        (error instanceof ApiError && error.detail) ||
        intl.formatMessage({ id: 'common.actionFailed' }),
    })
  }

  async function save(body: CreateRecurringRuleRequest) {
    try {
      if (rule) await update.mutateAsync({ id: rule.id, body: { ...body, version: rule.version } })
      else await create.mutateAsync(body)
    } catch (error) {
      failed(error)
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'recurrences.saved' }) })
    onClose()
  }

  async function confirmArchive() {
    if (!rule) return
    setConfirming(false)
    try {
      await archive.mutateAsync(rule.id)
    } catch (error) {
      failed(error)
      return
    }
    toast.show({ message: intl.formatMessage({ id: 'recurrences.archived' }) })
    onClose()
  }

  const archiveLabel = intl.formatMessage({ id: 'recurrences.archive' })
  return (
    <>
      <Sheet
        open
        onOpenChange={(open) => {
          if (!open) onClose()
        }}
        title={intl.formatMessage({ id: rule ? 'recurrences.edit' : 'recurrences.new' })}
      >
        <div className="flex flex-col gap-4">
          {!online && (
            <p className="rounded-card bg-bg p-3">
              <FormattedMessage id="transactions.offlineEditing" />
            </p>
          )}
          <RuleForm
            workspace={workspace}
            rule={rule}
            saveDisabled={!online || busy}
            onSave={(body) => void save(body)}
          />
          {rule && (
            <button
              type="button"
              disabled={!online || busy}
              onClick={() => setConfirming(true)}
              className="min-h-12 rounded-field border border-negative px-4 font-medium text-negative focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-50"
            >
              {archiveLabel}
            </button>
          )}
        </div>
      </Sheet>
      <ConfirmDialog
        open={confirming}
        title={archiveLabel}
        body={<FormattedMessage id="recurrences.archiveBody" />}
        confirmLabel={archiveLabel}
        cancelLabel={<FormattedMessage id="recurrences.archiveCancel" />}
        confirmDisabled={busy}
        onConfirm={() => void confirmArchive()}
        onCancel={() => setConfirming(false)}
      />
    </>
  )
}

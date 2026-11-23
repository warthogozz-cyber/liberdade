import { useIntl } from 'react-intl'
import { useQueryClient } from '@tanstack/react-query'
import {
  ApiError,
  queryKeys,
  restoreTransaction,
  type ConfirmTransactionRequest,
  type MeWorkspace,
  type TransactionView,
} from '@/shared/api'
import { useConfirmPlanned, useSkipPlanned } from '@/shared/api/recurrences'
import { useToast } from '@/shared/ui'
import './more.register'

/**
 * Confirming and skipping a planned transaction, with the notices both screens share. Both calls
 * need the connection (they never go through the queue), so the caller disables its buttons while
 * offline. A planned one that is gone or changed (404, 409) says so; the list is fetched again.
 */
export function usePlannedActions(workspace: MeWorkspace) {
  const intl = useIntl()
  const toast = useToast()
  const queryClient = useQueryClient()
  const confirm = useConfirmPlanned(workspace.id)
  const skip = useSkipPlanned(workspace.id)

  function failed(error: unknown) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 409)) {
      toast.show({ message: intl.formatMessage({ id: 'planned.changed' }) })
      return
    }
    console.error(error)
    toast.show({ message: intl.formatMessage({ id: 'common.actionFailed' }) })
  }

  /** Confirms `planned` as it was shown, or with the adjustments in `changes`; true once done. */
  async function confirmPlanned(
    planned: TransactionView,
    changes: Omit<ConfirmTransactionRequest, 'version'> = {},
  ): Promise<boolean> {
    try {
      await confirm.mutateAsync({ id: planned.id, body: { version: planned.version, ...changes } })
    } catch (error) {
      failed(error)
      return false
    }
    toast.show({ message: intl.formatMessage({ id: 'planned.confirmed' }) })
    return true
  }

  /** Skips `planned`, and offers Desfazer, which brings it back; true once skipped. */
  async function skipPlanned(planned: TransactionView): Promise<boolean> {
    try {
      await skip.mutateAsync({ id: planned.id, version: planned.version })
    } catch (error) {
      failed(error)
      return false
    }
    toast.show({
      message: intl.formatMessage({ id: 'planned.skipped' }),
      action: {
        label: intl.formatMessage({ id: 'transaction.undo' }),
        altText: intl.formatMessage({ id: 'transaction.undoAlt' }),
        onClick: () => {
          // Plain calls, not hooks: the screen that skipped may be gone by now.
          restoreTransaction(workspace.id, planned.id)
            .then(() =>
              queryClient.invalidateQueries({ queryKey: queryKeys.workspace(workspace.id) }),
            )
            .catch(failed)
        },
      },
    })
    return true
  }

  return { confirmPlanned, skipPlanned, busy: confirm.isPending || skip.isPending }
}

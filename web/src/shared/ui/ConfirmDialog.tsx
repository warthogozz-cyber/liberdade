import { AlertDialog } from 'radix-ui'
import type { MouseEvent, ReactNode } from 'react'

interface ConfirmDialogProps {
  open: boolean
  title: ReactNode
  body: ReactNode
  confirmLabel: ReactNode
  cancelLabel: ReactNode
  /** Disables the confirm button, for a call that is still running. */
  confirmDisabled?: boolean
  onConfirm: () => void
  /** Called for the cancel button and for Escape. */
  onCancel: () => void
}

const button =
  'min-h-12 rounded-field px-4 font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

/** Asks before a step that cannot be undone. The focus starts on the cancel button. */
export function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel,
  cancelLabel,
  confirmDisabled = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  // The confirm button would also close the dialog, which reads as a cancel: the caller closes it.
  function confirm(event: MouseEvent) {
    event.preventDefault()
    onConfirm()
  }

  return (
    <AlertDialog.Root
      open={open}
      onOpenChange={(next) => {
        if (!next) onCancel()
      }}
    >
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="fixed inset-0 bg-black/40" />
        <AlertDialog.Content className="fixed inset-x-4 top-1/2 mx-auto max-w-sm -translate-y-1/2 rounded-sheet bg-surface p-6 shadow-lg">
          <AlertDialog.Title className="text-lg font-semibold">{title}</AlertDialog.Title>
          <AlertDialog.Description className="mt-2 text-text-muted">{body}</AlertDialog.Description>
          <div className="mt-6 flex flex-col gap-2">
            <AlertDialog.Cancel className={`${button} border border-primary text-primary`}>
              {cancelLabel}
            </AlertDialog.Cancel>
            <AlertDialog.Action
              className={`${button} bg-primary text-on-primary disabled:opacity-50`}
              disabled={confirmDisabled}
              onClick={confirm}
            >
              {confirmLabel}
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  )
}

import { Dialog } from 'radix-ui'
import type { ReactNode } from 'react'
import { useIntl } from 'react-intl'

interface SheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: ReactNode
  children: ReactNode
}

/** A dialog held to the bottom edge of the screen, closed by Fechar, Escape or a tap outside. */
export function Sheet({ open, onOpenChange, title, children }: SheetProps) {
  const intl = useIntl()
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40" />
        {/* The title names the sheet; its contents vary too much for one description. */}
        <Dialog.Content
          aria-describedby={undefined}
          className="fixed inset-x-0 bottom-0 z-40 mx-auto max-h-[85vh] max-w-lg overflow-y-auto rounded-t-sheet bg-surface p-6 shadow-lg"
        >
          <div className="mb-4 flex items-center justify-between gap-4">
            <Dialog.Title className="text-lg font-semibold">{title}</Dialog.Title>
            <Dialog.Close
              aria-label={intl.formatMessage({ id: 'sheet.close' })}
              className="flex size-12 items-center justify-center rounded-field text-text-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              <span aria-hidden="true">✕</span>
            </Dialog.Close>
          </div>
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

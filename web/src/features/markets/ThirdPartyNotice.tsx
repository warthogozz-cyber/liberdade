import { useState, type ReactNode } from 'react'
import { FormattedMessage } from 'react-intl'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { registerMessages } from '@/shared/i18n'
import { marketsMessages } from './messages.pt-BR'

registerMessages(marketsMessages)

interface ThirdPartyNoticeProps {
  title: ReactNode
  /** What the third party receives, in the person's words. */
  text: ReactNode
  confirmLabel: ReactNode
  /** The person accepted; `remember` is true when "Não perguntar de novo" was checked. */
  onConfirm: (remember: boolean) => void
  onCancel: () => void
}

/** The notice before anything is loaded from a third party: accept once, or accept and stop asking. */
export function ThirdPartyNotice({
  title,
  text,
  confirmLabel,
  onConfirm,
  onCancel,
}: ThirdPartyNoticeProps) {
  const [remember, setRemember] = useState(false)
  return (
    <ConfirmDialog
      open
      title={title}
      body={
        <span className="flex flex-col gap-3">
          <span>{text}</span>
          <label className="flex min-h-12 items-center gap-3 text-text">
            <input
              type="checkbox"
              checked={remember}
              onChange={(event) => setRemember(event.target.checked)}
              className="size-5 accent-primary"
            />
            <FormattedMessage id="markets.notice.dontAsk" />
          </label>
        </span>
      }
      confirmLabel={confirmLabel}
      cancelLabel={<FormattedMessage id="markets.notice.later" />}
      onConfirm={() => onConfirm(remember)}
      onCancel={onCancel}
    />
  )
}

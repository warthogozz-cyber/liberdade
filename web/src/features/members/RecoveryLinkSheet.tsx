import { useIntl } from 'react-intl'
import { Sheet } from '@/shared/ui/Sheet'
import { InvitationLink } from './InvitationLink'
import './register'

interface RecoveryLinkSheetProps {
  token: string
  onClose: () => void
}

/** The recovery link (24 hours, once), shown right after it is made: it cannot be read again. */
export function RecoveryLinkSheet({ token, onClose }: RecoveryLinkSheetProps) {
  const intl = useIntl()
  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
      title={intl.formatMessage({ id: 'members.recovery.title' })}
    >
      <InvitationLink token={token} hint={intl.formatMessage({ id: 'members.recovery.hint' })} />
    </Sheet>
  )
}

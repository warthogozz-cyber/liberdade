import { useIntl } from 'react-intl'
import type { MemberView } from '@/shared/api'
import { memberName } from './memberName'
import './register'

interface MemberRowProps {
  member: MemberView
  /** Tapping opens the member's sheet; without it the row is plain text. */
  onOpen?: () => void
}

const row = 'flex min-h-12 w-full items-center justify-between gap-2 text-left'

/** One person: the name, who they are to the family, and "Você" on the signed-in person's own row. */
export function MemberRow({ member, onOpen }: MemberRowProps) {
  const intl = useIntl()
  const content = (
    <>
      <span className="font-medium">{memberName(member, intl)}</span>
      <span className="text-sm text-text-muted">
        {member.you && `${intl.formatMessage({ id: 'members.you' })} · `}
        {intl.formatMessage({ id: `members.role.${member.role}` })}
      </span>
    </>
  )
  return onOpen ? (
    <button
      type="button"
      onClick={onOpen}
      className={`${row} focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary`}
    >
      {content}
    </button>
  ) : (
    <div className={row}>{content}</div>
  )
}

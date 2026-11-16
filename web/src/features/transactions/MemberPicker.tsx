import { FormattedMessage, useIntl } from 'react-intl'
import { useMembers } from '@/shared/api/family'
import { memberName } from '@/features/members/memberName'
import './more.register'

interface MemberPickerProps {
  workspaceId: string
  /** The signed-in person's membership, who spent unless another is chosen. */
  selfId: string
  /** The member chosen; `selfId` while nobody else is. */
  value: string
  /** The label comes along so the entry never needs the members' naming to show who was chosen. */
  onChange: (memberId: string, label: string) => void
}

const option =
  'flex min-h-12 cursor-pointer items-center gap-3 rounded-field px-3 has-checked:font-semibold has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-primary has-disabled:cursor-default has-disabled:text-text-muted'

/**
 * Who spent the money: the people still in the family, the signed-in one first as "Você". Without
 * the list (offline, before it loads) only "Você" shows, disabled, and saving is never blocked.
 */
export default function MemberPicker({ workspaceId, selfId, value, onChange }: MemberPickerProps) {
  const intl = useIntl()
  const members = useMembers(workspaceId).data
  const active = (members ?? []).filter((each) => each.status === 'ACTIVE')
  const others = active.filter((each) => each.id !== selfId)
  const you = intl.formatMessage({ id: 'members.you' })
  const canChoose = others.length > 0

  return (
    <fieldset>
      <legend className="mb-2 font-medium">
        <FormattedMessage id="transactions.who" />
      </legend>
      <label className={option}>
        <input
          type="radio"
          name="member"
          disabled={!canChoose}
          checked={value === selfId || !canChoose}
          onChange={() => onChange(selfId, you)}
        />
        {you}
      </label>
      {others.map((member) => (
        <label key={member.id} className={option}>
          <input
            type="radio"
            name="member"
            checked={member.id === value}
            onChange={() => onChange(member.id, memberName(member, intl))}
          />
          {memberName(member, intl)}
        </label>
      ))}
    </fieldset>
  )
}

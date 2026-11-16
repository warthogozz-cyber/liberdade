import { useId } from 'react'
import { useIntl } from 'react-intl'
import { memberName } from '@/features/members/memberName'
import { memberLabel } from '@/features/transactions/memberLabel'
import { useMembers } from '@/shared/api/family'
import type { MeWorkspace } from '@/shared/api'
import './register'

interface OwnerPickerProps {
  workspace: MeWorkspace
  /** The membership who owns the account, or `null` for nobody. */
  value: string | null
  onChange: (membershipId: string | null) => void
  disabled?: boolean
}

const option =
  'flex min-h-12 cursor-pointer items-center gap-3 rounded-field border border-text-muted/30 px-3 has-[:checked]:border-primary has-[:checked]:bg-primary/10 has-[:disabled]:opacity-50'

/** Who an account belongs to ("Titular"): one of the people in the family, or "Ninguém". */
export function OwnerPicker({ workspace, value, onChange, disabled }: OwnerPickerProps) {
  const intl = useIntl()
  const ids = useId()
  const all = useMembers(workspace.id).data ?? []
  const members = all.filter((each) => each.status === 'ACTIVE')
  // An owner who has left stays shown, so the form does not look unset; picking anyone else drops them.
  const left = all.find((each) => each.id === value && each.status !== 'ACTIVE')
  const choose = onChange

  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-1 font-medium">
        {intl.formatMessage({ id: 'accounts.field.owner' })}
      </legend>
      <div className="grid grid-cols-2 gap-2">
        <label className={option}>
          <input
            type="radio"
            name={`${ids}-owner`}
            disabled={disabled}
            checked={value === null}
            onChange={() => choose(null)}
          />
          {intl.formatMessage({ id: 'accounts.owner.none' })}
        </label>
        {left && (
          <label className={option}>
            <input type="radio" name={`${ids}-owner`} checked disabled readOnly />
            {memberLabel(left, intl)}
          </label>
        )}
        {members.map((member) => (
          <label key={member.id} className={option}>
            <input
              type="radio"
              name={`${ids}-owner`}
              disabled={disabled}
              checked={value === member.id}
              onChange={() => choose(member.id)}
            />
            {memberName(member, intl)}
          </label>
        ))}
      </div>
    </fieldset>
  )
}

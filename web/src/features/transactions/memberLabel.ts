import type { IntlShape } from 'react-intl'
import type { MemberView } from '@/shared/api'
import { memberName } from '@/features/members/memberName'
import './more.register'

/**
 * Who a transaction is credited to, as the lists show it: the name, with "(saiu)" for someone who
 * left, and "Ex-membro" when a person who left has no name. Nothing for a member not loaded yet.
 */
export function memberLabel(member: MemberView | undefined, intl: IntlShape): string | undefined {
  if (!member) return undefined
  const name = memberName(member, intl)
  return member.status === 'REMOVED' && (member.nickname || member.displayName)
    ? intl.formatMessage({ id: 'transactions.memberLeft' }, { name })
    : name
}

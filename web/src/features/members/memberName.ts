import type { IntlShape } from 'react-intl'
import type { MemberView } from '@/shared/api'
import './register'

/** How the family sees a person: their nickname, else their name, else "Ex-membro". */
export function memberName(
  member: Pick<MemberView, 'nickname' | 'displayName'>,
  intl: IntlShape,
): string {
  return member.nickname || member.displayName || intl.formatMessage({ id: 'members.formerMember' })
}

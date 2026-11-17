import type { IntlShape } from 'react-intl'
import type { ActivityItem } from '@/shared/api/privacy'

/**
 * The sentence for one activity entry, told from the signed-in person's side (the history has no
 * target names, D18). `<action>[.<variant>].(me|other)` is the message id; an action the app does not
 * know yet falls back to a generic line.
 */
export function activityPhrase(intl: IntlShape, item: ActivityItem): string {
  const known = (id: string) => id in intl.messages
  const actor = item.actorName ?? intl.formatMessage({ id: 'activity.formerMember' })
  const metadata = item.metadata
  const role = (value: string | undefined) =>
    value && known(`activity.role.${value}`)
      ? intl.formatMessage({ id: `activity.role.${value}` as 'activity.role.OWNER' })
      : ''
  const moduleName = (value: string | undefined) =>
    value && known(`activity.module.${value}`)
      ? intl.formatMessage({ id: `activity.module.${value}` as 'activity.module.planning' })
      : (value ?? '')
  const perspective = item.byMe ? 'me' : 'other'

  const variant = (): string | undefined => {
    switch (item.action) {
      case 'auth.signed_in':
        return metadata.method
      case 'data.exported':
        return metadata.scope
      case 'module.toggled':
        return metadata.enabled === 'true' ? 'on' : 'off'
      default:
        return undefined
    }
  }
  const part = variant()

  // Entries with their own wording that does not depend on who did it.
  const fixed: string[] = []
  if (item.action === 'auth.signed_in') {
    fixed.push(`activity.auth.signed_in.${part ?? ''}`, 'activity.auth.signed_in')
  }
  if (item.action === 'documents.accepted') fixed.push('activity.documents.accepted')
  if (item.action === 'passkey.removed') fixed.push('activity.passkey.removed')
  if (item.action === 'member.removed' && metadata.reason) {
    fixed.push(`activity.member.removed.${metadata.reason}`)
  }
  const fixedId = fixed.find(known)
  if (fixedId) return intl.formatMessage({ id: fixedId as 'activity.auth.signed_in' })

  const id = ['activity', item.action, part, perspective].filter(Boolean).join('.')
  if (!known(id)) return intl.formatMessage({ id: 'activity.unknown' })
  return intl.formatMessage(
    { id: id as 'activity.member.joined.me' },
    {
      actor,
      role: role(metadata.to ?? metadata.role),
      module: moduleName(metadata.key),
    },
  )
}

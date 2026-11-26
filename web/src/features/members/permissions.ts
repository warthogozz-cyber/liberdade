import type { MemberRole, MemberView } from '@/shared/api'

// These mirror the API's rules to decide what to show. The API still answers for every call.

/** Owners and Adults invite people, cancel invitations and bring passkey-only members back. */
export function canInvite(role: MemberRole): boolean {
  return role === 'OWNER' || role === 'ADULT'
}

/** The roles `viewer` may hand out in an invitation: only an Owner invites another Owner. */
export function invitableRoles(viewer: MemberRole): MemberRole[] {
  return viewer === 'OWNER' ? ['OWNER', 'ADULT', 'YOUTH', 'VIEWER'] : ['ADULT', 'YOUTH', 'VIEWER']
}

/** A recovery link is for a member who has only a passkey; an Adult cannot make one for an Owner. */
export function canRecover(viewer: MemberRole, member: MemberView): boolean {
  if (member.status !== 'ACTIVE' || !member.passkeyOnly) return false
  return viewer === 'OWNER' || (viewer === 'ADULT' && member.role !== 'OWNER')
}

/** Whether tapping the member opens something: Owners manage everyone, Adults only recover. */
export function canOpenMember(viewer: MemberRole, member: MemberView): boolean {
  return member.status === 'ACTIVE' && (viewer === 'OWNER' || canRecover(viewer, member))
}

/** The family's address for the Feira is for Owners and Adults only (it is where the family lives). */
export function canSetMarketHome(role: MemberRole): boolean {
  return role === 'OWNER' || role === 'ADULT'
}

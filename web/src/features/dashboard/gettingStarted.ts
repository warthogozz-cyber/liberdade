import type { MemberRole } from '@/shared/api'

/** How many accounts finish the "add your accounts" step, archived ones counted. */
export const ACCOUNTS_DONE_AT = 2

export interface GettingStartedState {
  /** The family has a transaction that is not deleted. */
  hasTransactions: boolean
  /** The member has an expense waiting in the queue on this device. */
  queued: boolean
  /** When this member closed the card, `null` while they have not. */
  dismissedAt: string | null
  /** Every account of the family, archived ones too. */
  accountCount: number
  hasPasskey: boolean
  /** The member's role in the family, which decides whether inviting is one of their steps. */
  role: MemberRole
  /** Someone was invited to the family. */
  familyInvited: boolean
}

/** Only an Owner or an Adult can invite, so only they are asked to. */
export function isAskedToInvite(role: MemberRole): boolean {
  return role === 'OWNER' || role === 'ADULT'
}

/**
 * Whether Home shows the getting-started card (D15): once the family has started using the app,
 * until the member closes it or every step is done.
 */
export function shouldShowGettingStarted({
  hasTransactions,
  queued,
  dismissedAt,
  accountCount,
  hasPasskey,
  role,
  familyInvited,
}: GettingStartedState): boolean {
  const started = hasTransactions || queued
  const inviteDone = familyInvited || !isAskedToInvite(role)
  const allDone = accountCount >= ACCOUNTS_DONE_AT && hasPasskey && inviteDone
  return started && dismissedAt === null && !allDone
}

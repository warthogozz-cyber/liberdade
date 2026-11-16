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
}: GettingStartedState): boolean {
  const started = hasTransactions || queued
  const allDone = accountCount >= ACCOUNTS_DONE_AT && hasPasskey
  return started && dismissedAt === null && !allDone
}

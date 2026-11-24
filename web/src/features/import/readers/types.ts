/** One statement entry as read from the file; the sign of `cents` is the file's own. */
export type StatementLine = {
  /** yyyy-MM-dd */
  occurredOn: string
  /** Signed amount in cents. */
  cents: bigint
  description: string
}

/** Most entries one statement may hold; more asks the person for a shorter period. */
export const MAX_STATEMENT_LINES = 2000

export type StatementReadReason = 'unreadable' | 'empty' | 'tooLarge'

export class StatementReadError extends Error {
  readonly reason: StatementReadReason

  constructor(reason: StatementReadReason) {
    super(reason)
    this.name = 'StatementReadError'
    this.reason = reason
  }
}

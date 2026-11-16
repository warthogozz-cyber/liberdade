/** How long the recovery notice stays on Home. */
export const RECOVERY_NOTICE_DAYS = 7

const DAY_MS = 24 * 60 * 60 * 1000

/** Whether `recoveredAt` is recent enough for the notice. Pure, so Home can ask before loading it. */
export function isRecoveryNoticeRecent(recoveredAt: string | null, now: number): boolean {
  if (recoveredAt === null) return false
  const age = now - Date.parse(recoveredAt)
  return age >= 0 && age < RECOVERY_NOTICE_DAYS * DAY_MS
}

export function recoveryNoticeKey(membershipId: string): string {
  return `liberdade.recoveryNotice.${membershipId}`
}

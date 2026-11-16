import { negateCents } from '@/shared/money'

/** The balance a keypad amount and its sign stand for. */
export function signedCents(cents: bigint, negative: boolean): bigint {
  return negative ? negateCents(cents) : cents
}

import type { TransactionView } from '@/shared/api'
import { parseCents } from '@/shared/money/money'
import type { KindedLine } from './kinds'
import { normalizeDescription } from './normalize'

export type PreviewState =
  | 'alreadyImported'
  | 'balance'
  | 'confirmPlanned'
  | 'possibleRepeat'
  | 'billPayment'
  | 'normal'

export type PreviewLine = KindedLine & {
  id: string
  importKey: string
  state: PreviewState
  checked: boolean
  /** On a `confirmPlanned` line, the planned transaction's category, which the API keeps (read-only here). */
  categoryId: string | null
  confirmsPlannedId: string | null
}

/**
 * A running balance printed as a line (SALDO ANTERIOR, SALDO DO DIA, S A L D O): its normalized description,
 * spaces removed, starts with "saldo". It is no transaction, so it never starts checked.
 */
export function looksLikeBalance(description: string): boolean {
  return normalizeDescription(description).replace(/ /g, '').startsWith('saldo')
}

/** A planned transaction confirms a line dated at most this many days away. */
const PLANNED_WINDOW_DAYS = 5

const DAY_MS = 86_400_000

function dayNumber(date: string): number {
  return Math.floor(Date.parse(`${date}T00:00:00Z`) / DAY_MS)
}

function shiftDate(date: string, days: number): string {
  return new Date(Date.parse(`${date}T00:00:00Z`) + days * DAY_MS).toISOString().slice(0, 10)
}

/** The dates the existing transactions are fetched for: the file's first and last day, five days wider. */
export function previewRange(lines: KindedLine[]): { from: string; to: string } {
  let min = lines[0].occurredOn
  let max = min
  for (const l of lines) {
    if (l.occurredOn < min) min = l.occurredOn
    if (l.occurredOn > max) max = l.occurredOn
  }
  return { from: shiftDate(min, -PLANNED_WINDOW_DAYS), to: shiftDate(max, PLANNED_WINDOW_DAYS) }
}

/**
 * Pairs lines with planned transactions of the same kind and amount, at most five days apart. The smallest gap
 * is paired first and a tie goes to the line earlier in the file; each planned transaction and each line is
 * used once, so no planned transaction is confirmed twice.
 */
function matchPlanned(
  lines: KindedLine[],
  candidates: number[],
  planned: TransactionView[],
): Map<number, TransactionView> {
  const pairs: Array<{ line: number; planned: number; gap: number }> = []
  planned.forEach((p, pi) => {
    const plannedDay = dayNumber(p.scheduledOn ?? p.occurredOn)
    const cents = parseCents(p.amount.amount)
    for (const li of candidates) {
      const l = lines[li]
      if (l.kind !== p.kind || l.absCents !== cents) continue
      const gap = Math.abs(dayNumber(l.occurredOn) - plannedDay)
      if (gap <= PLANNED_WINDOW_DAYS) pairs.push({ line: li, planned: pi, gap })
    }
  })
  pairs.sort((a, b) => a.gap - b.gap || a.line - b.line || a.planned - b.planned)

  const matched = new Map<number, TransactionView>()
  const used = new Set<number>()
  for (const pair of pairs) {
    if (matched.has(pair.line) || used.has(pair.planned)) continue
    matched.set(pair.line, planned[pair.planned])
    used.add(pair.planned)
  }
  return matched
}

/**
 * Gives each line its state, in this precedence: already imported (its key is on a transaction), a balance line,
 * confirms a planned one (only with planning on, and only a planned one with a category, which it keeps),
 * possible repeat (a manual confirmed transaction with the same date, amount, kind and description), bill
 * payment, normal. Only `normal` and `confirmPlanned` start checked. `existing` holds the account's transactions
 * around the file's dates, planned ones included.
 */
export function buildPreview(
  lines: KindedLine[],
  keys: string[],
  existing: TransactionView[],
  planningOn: boolean,
  newId: () => string,
): PreviewLine[] {
  const importedKeys = new Set<string>()
  const manual = new Set<string>()
  const planned: TransactionView[] = []
  for (const t of existing) {
    if (t.importKey) importedKeys.add(t.importKey)
    if (t.status === 'PLANNED') {
      if (t.categoryId !== null) planned.push(t)
    } else if (!t.importKey) {
      manual.add(
        repeatSignature(
          t.occurredOn,
          parseCents(t.amount.amount),
          t.kind,
          normalizeDescription(t.description ?? ''),
        ),
      )
    }
  }

  const imported = lines.map((_, i) => importedKeys.has(keys[i]))
  const balance = lines.map((l) => looksLikeBalance(l.description))
  const candidates = lines.map((_, i) => i).filter((i) => !imported[i] && !balance[i])
  const confirms = planningOn
    ? matchPlanned(lines, candidates, planned)
    : new Map<number, TransactionView>()

  return lines.map((l, i) => {
    const base = { ...l, id: newId(), importKey: keys[i], categoryId: null }
    if (imported[i]) {
      return { ...base, state: 'alreadyImported', checked: false, confirmsPlannedId: null }
    }
    if (balance[i]) {
      return { ...base, state: 'balance', checked: false, confirmsPlannedId: null }
    }
    const confirmed = confirms.get(i)
    if (confirmed !== undefined) {
      return {
        ...base,
        state: 'confirmPlanned',
        checked: true,
        categoryId: confirmed.categoryId,
        confirmsPlannedId: confirmed.id,
      }
    }
    const repeat = manual.has(
      repeatSignature(l.occurredOn, l.absCents, l.kind, normalizeDescription(l.description)),
    )
    if (repeat) return { ...base, state: 'possibleRepeat', checked: false, confirmsPlannedId: null }
    if (l.billPayment) {
      return { ...base, state: 'billPayment', checked: false, confirmsPlannedId: null }
    }
    return { ...base, state: 'normal', checked: true, confirmsPlannedId: null }
  })
}

function repeatSignature(date: string, cents: bigint, kind: string, description: string): string {
  return `${date}|${cents}|${kind}|${description}`
}

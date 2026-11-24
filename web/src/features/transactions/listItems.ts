import type { AccountView, CategoryView, TransactionView } from '@/shared/api'
import type { OutboxItem } from '@/shared/offline'

/** A row of the list: a transaction from the API, or a queued one with its `outbox` item. */
export type ListItem = TransactionView & { createdAtMs: number; outbox?: OutboxItem }

export interface DayGroup {
  /** `YYYY-MM-DD`. */
  date: string
  items: ListItem[]
}

/** Newest first: by date, then by creation, then by id, as the API lists them. */
function newestFirst(a: ListItem, b: ListItem): number {
  return (
    b.occurredOn.localeCompare(a.occurredOn) ||
    b.createdAtMs - a.createdAtMs ||
    b.id.localeCompare(a.id)
  )
}

function fromOutbox(item: OutboxItem): ListItem {
  return {
    ...item.payload,
    toAccountId: item.payload.toAccountId ?? null,
    categoryId: item.payload.categoryId ?? null,
    description: item.payload.description ?? null,
    paymentMethod: item.payload.paymentMethod ?? null,
    tags: (item.payload.tags ?? []).filter((name) => name !== null),
    status: 'CONFIRMED',
    scheduledOn: null,
    recurringRuleId: null,
    importKey: null,
    // Not stored yet, so at no version of the API's.
    version: 0,
    // Without a chosen member the API credits the signed-in one; '' stands for that here.
    memberId: item.payload.memberId ?? '',
    createdAt: new Date(item.createdAt).toISOString(),
    createdAtMs: item.createdAt,
    outbox: item,
  }
}

/**
 * The month from the API joined with the whole queue, of any month, so no refused item stays
 * hidden. An id in both is the API's: it was sent and the queue has not caught up yet.
 */
export function mergeTransactions(
  server: TransactionView[] | undefined,
  outbox: OutboxItem[],
): ListItem[] {
  // A planned one has not happened: it has its own section, outside the days and the totals.
  const sent = (server ?? []).filter(({ status }) => status !== 'PLANNED')
  const ids = new Set(sent.map((transaction) => transaction.id))
  return [
    ...sent.map((transaction) => ({
      ...transaction,
      createdAtMs: Date.parse(transaction.createdAt),
    })),
    ...outbox.filter((item) => !ids.has(item.id)).map(fromOutbox),
  ].sort(newestFirst)
}

/** The planned ones of the month, the soonest first. */
export function plannedOf(server: TransactionView[] | undefined): TransactionView[] {
  return (server ?? [])
    .filter(({ status }) => status === 'PLANNED')
    .sort(
      (a, b) =>
        (a.scheduledOn ?? a.occurredOn).localeCompare(b.scheduledOn ?? b.occurredOn) ||
        a.id.localeCompare(b.id),
    )
}

/** Groups items already sorted newest first by their date, keeping that order. */
export function groupByDay(items: ListItem[]): DayGroup[] {
  const groups: DayGroup[] = []
  for (const item of items) {
    const last = groups.at(-1)
    if (last?.date === item.occurredOn) last.items.push(item)
    else groups.push({ date: item.occurredOn, items: [item] })
  }
  return groups
}

/** A category as the screens show it: "{icon} {name}". */
export function categoryLabel({ icon, name }: CategoryView): string {
  return icon ? `${icon} ${name}` : name
}

/**
 * "Origem → Destino" for a transfer; the names stay for archived accounts. `unseen` names an
 * account the list does not hold, which for a Youth is one of the family's they cannot see.
 */
export function transferRoute(
  { accountId, toAccountId }: Pick<TransactionView, 'accountId' | 'toAccountId'>,
  accounts: Map<string, Pick<AccountView, 'name'>>,
  unseen = '',
): string {
  const from = accounts.get(accountId)?.name ?? unseen
  const to = toAccountId ? (accounts.get(toAccountId)?.name ?? unseen) : ''
  return `${from} → ${to}`
}

/**
 * Whether the person may confirm, skip or edit a planned transaction. A youth may only when every
 * account it touches is one of theirs, the same as the server's rule (the youth's account list holds
 * only their own accounts); anyone else but a viewer always may.
 */
export function canActOnPlanned(
  role: string,
  planned: Pick<TransactionView, 'accountId' | 'toAccountId'>,
  held: { has(accountId: string): boolean },
): boolean {
  if (role === 'VIEWER') return false
  if (role !== 'YOUTH') return true
  return held.has(planned.accountId) && (!planned.toAccountId || held.has(planned.toAccountId))
}

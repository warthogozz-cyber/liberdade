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
    // Not stored yet, so at no version of the API's.
    version: 0,
    // The API sets the member when it stores the transaction; a queued one is always the user's.
    memberId: '',
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
  const sent = server ?? []
  const ids = new Set(sent.map((transaction) => transaction.id))
  return [
    ...sent.map((transaction) => ({
      ...transaction,
      createdAtMs: Date.parse(transaction.createdAt),
    })),
    ...outbox.filter((item) => !ids.has(item.id)).map(fromOutbox),
  ].sort(newestFirst)
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

/** "Origem → Destino" for a transfer; the names stay for archived accounts. */
export function transferRoute(
  { accountId, toAccountId }: Pick<TransactionView, 'accountId' | 'toAccountId'>,
  accounts: Map<string, AccountView>,
): string {
  const from = accounts.get(accountId)?.name ?? ''
  const to = toAccountId ? (accounts.get(toAccountId)?.name ?? '') : ''
  return `${from} → ${to}`
}

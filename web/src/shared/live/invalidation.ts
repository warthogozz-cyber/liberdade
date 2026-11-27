import { queryKeys } from '@/shared/api'
import { gamificationKeys } from '@/shared/api/gamification'
import { planningKeys } from '@/shared/api/planning'

const WS = ''

/** What follows `['ws', ws]` in a key. */
function after(key: readonly unknown[]): readonly string[] {
  return key.slice(queryKeys.workspace(WS).length) as readonly string[]
}

const transactions = after(queryKeys.transactions(WS, '').slice(0, 3))
const dashboard = after(queryKeys.dashboard(WS, '').slice(0, 3))
const accounts = after(queryKeys.accounts(WS))
const categories = after(queryKeys.categories(WS, 'EXPENSE').slice(0, 3))
const tags = after(queryKeys.tags(WS))
const rules = after(queryKeys.rules(WS))
const members = after(queryKeys.members(WS))
const invitations = after(queryKeys.invitations(WS))
const modules = after(queryKeys.modules(WS))
const planning = after(planningKeys.all(WS))
const budgets = after(planningKeys.budgets(WS, '').slice(0, 4))
const goals = after(planningKeys.goals(WS))
const reserve = after(planningKeys.reserve(WS))
const projection = after(planningKeys.projection(WS))
const gamification = after(gamificationKeys.all(WS))
// The prefix of `venturesKeys.all` (shared/api/ventures.ts), written out so the initial chunk does not
// take that file for one key; the live-invalidation test checks they agree.
const ventures = ['ventures'] as const
// The prefixes of `marketsKeys.list`, `.compare` and `.products` (shared/api/markets.ts), written out for the same
// reason; the live-invalidation test checks they agree. A list change also changes the comparison, and a product
// rename or join changes both, so the one notice reaches all three.
const marketsList = ['markets', 'list'] as const
const marketsCompare = ['markets', 'compare'] as const
const marketsProducts = ['markets', 'products'] as const

const PREFIXES: Record<string, (readonly string[])[]> = {
  transaction: [transactions, dashboard, accounts, tags, planning, gamification, ventures],
  account: [accounts, transactions, dashboard, planning, gamification],
  category: [categories, transactions, dashboard, planning],
  tag: [tags, transactions],
  rule: [rules, transactions, planning],
  budget: [budgets],
  goal: [goals],
  reserve: [reserve, projection],
  members: [members, invitations, accounts],
  modules: [modules, planning, ventures],
  achievement: [gamification],
  venture: [ventures],
  'shopping-list': [marketsList, marketsCompare, marketsProducts],
}

/**
 * The key prefixes, after `['ws', ws]`, that an `entity` change makes stale. An entity this app does
 * not know invalidates the whole family (the empty prefix).
 */
export function invalidationPrefixes(entity: string): (readonly string[])[] {
  return PREFIXES[entity]?.map((prefix) => [...prefix]) ?? [[]]
}

/** Whether an `entity` change also makes the signed-in person's own view stale (the role in `['me']`). */
export function invalidatesMe(entity: string): boolean {
  return entity === 'members'
}

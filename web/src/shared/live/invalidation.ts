import { queryKeys } from '@/shared/api'
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

const PREFIXES: Record<string, (readonly string[])[]> = {
  transaction: [transactions, dashboard, accounts, tags, planning],
  account: [accounts, transactions, dashboard, planning],
  category: [categories, transactions, dashboard, planning],
  tag: [tags, transactions],
  rule: [rules, transactions, planning],
  budget: [budgets],
  goal: [goals],
  reserve: [reserve, projection],
  members: [members, invitations, accounts],
  modules: [modules, planning],
}

/**
 * The key prefixes, after `['ws', ws]`, that an `entity` change makes stale. An entity this app does
 * not know invalidates the whole family (the empty prefix).
 */
export function invalidationPrefixes(entity: string): (readonly string[])[] {
  return PREFIXES[entity]?.map((prefix) => [...prefix]) ?? [[]]
}

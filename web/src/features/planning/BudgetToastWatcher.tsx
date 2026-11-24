import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { useIntl } from 'react-intl'
import { queryKeys, useFormatMoney } from '@/shared/api'
import { listCategories } from '@/shared/api/endpoints'
import { getBudgets, planningKeys } from '@/shared/api/planning'
import { onLocalAction, type LocalAction } from '@/shared/events'
import { parseCents } from '@/shared/money'
import { useToast } from '@/shared/ui'
import { budgetToastFor, readToastSeen, toastStorageKey, writeToastSeen } from './budgetToast'
import './register'

const CATEGORIES_FRESH_MS = 60_000

/**
 * Invisible: after an expense saved on this device, says once when its category (or the parent
 * whose limit it counts in) reaches 80% or 100% of the month's limit (D18). Queued saves stay
 * silent, and so does a failing call or storage; there is no sound and no vibration.
 */
export default function BudgetToastWatcher() {
  const intl = useIntl()
  const toast = useToast()
  const queryClient = useQueryClient()
  const format = useFormatMoney()
  // The listener subscribes once; it reads the latest of these through the ref.
  const latest = useRef({ intl, toast, format })
  useEffect(() => {
    latest.current = { intl, toast, format }
  })

  useEffect(() => {
    // One at a time, so two saves in a row cannot both find the threshold unseen.
    let queue: Promise<void> = Promise.resolve()

    async function check(action: LocalAction & { categoryId: string; month: string }) {
      const ws = action.workspaceId
      const [budgets, categories] = await Promise.all([
        queryClient.fetchQuery({
          queryKey: planningKeys.budgets(ws, action.month),
          queryFn: () => getBudgets(ws, action.month),
          staleTime: 0,
        }),
        queryClient.fetchQuery({
          queryKey: queryKeys.categories(ws, 'EXPENSE'),
          queryFn: () => listCategories(ws, 'EXPENSE'),
          staleTime: CATEGORIES_FRESH_MS,
        }),
      ])
      const own = categories.find((each) => each.id === action.categoryId)
      const result = budgetToastFor(
        budgets.lines,
        action.categoryId,
        own?.parentId ?? null,
        (key) => readToastSeen(toastStorageKey(ws, action.month, key)),
      )
      if (!result) return
      const name = categories.find((each) => each.id === result.categoryId)?.name
      if (!name) return
      const { intl: i, toast: t, format: money } = latest.current
      const message =
        result.message === 'over' && result.overBy
          ? i.formatMessage(
              { id: 'planning.budget.toast.over' },
              { category: name, amount: money(parseCents(result.overBy.amount)) },
            )
          : i.formatMessage(
              {
                id:
                  result.message === 'at'
                    ? 'planning.budget.toast.at'
                    : 'planning.budget.toast.near',
              },
              { category: name },
            )
      for (const key of result.keys) writeToastSeen(toastStorageKey(ws, action.month, key))
      t.show({ message })
    }

    const off = onLocalAction((action) => {
      const { categoryId, month } = action
      if (action.type !== 'transaction-saved' || action.fromQueue || !categoryId || !month) return
      queue = queue
        .then(() => check({ ...action, categoryId, month }))
        // A failed call (offline, a Youth, the module off) only means no notice this time.
        .catch(() => undefined)
    })
    return off
  }, [queryClient])

  return null
}

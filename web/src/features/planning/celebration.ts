import { useCallback, useState } from 'react'

/** A goal reached longer ago than this is listed without a celebration (D20). */
const RECENT_MS = 7 * 24 * 60 * 60 * 1000

interface Reachable {
  id: string
  achievedAt: string | null
}

/**
 * Which reached goals to celebrate now and which to remember without a word: a goal reached in the
 * last 7 days that was not celebrated yet is celebrated; an older one only joins the celebrated set,
 * so a new device does not release a queue of old celebrations.
 */
export function goalsToCelebrate(
  goals: readonly Reachable[],
  celebrated: ReadonlySet<string>,
  now: Date,
): { celebrate: string[]; markSilently: string[] } {
  const celebrate: string[] = []
  const markSilently: string[] = []
  for (const goal of goals) {
    if (goal.achievedAt === null || celebrated.has(goal.id)) continue
    const age = now.getTime() - new Date(goal.achievedAt).getTime()
    if (age <= RECENT_MS) celebrate.push(goal.id)
    else markSilently.push(goal.id)
  }
  return { celebrate, markSilently }
}

export function celebratedStorageKey(workspaceId: string): string {
  return `liberdade.goal-celebrated:${workspaceId}`
}

/** What this session celebrated, for when the device storage fails: at most once per session. */
const celebratedInSession = new Map<string, Set<string>>()

function readCelebrated(workspaceId: string): Set<string> {
  const ids = new Set(celebratedInSession.get(workspaceId))
  try {
    const raw = localStorage.getItem(celebratedStorageKey(workspaceId))
    const stored: unknown = JSON.parse(raw ?? '[]')
    if (Array.isArray(stored)) {
      for (const id of stored) if (typeof id === 'string') ids.add(id)
    }
  } catch {
    // Unreadable storage reads as nothing celebrated on the device; the session set still holds.
  }
  return ids
}

function writeCelebrated(workspaceId: string, ids: ReadonlySet<string>): void {
  celebratedInSession.set(workspaceId, new Set(ids))
  try {
    localStorage.setItem(celebratedStorageKey(workspaceId), JSON.stringify([...ids]))
  } catch {
    // Without storage a new session may celebrate again; nothing else depends on it.
  }
}

/**
 * The goals to celebrate now, remembered as celebrated before they are returned, so two screens
 * looking at the same data never celebrate the same goal twice.
 */
export function celebrateNew<Goal extends Reachable>(
  workspaceId: string,
  goals: readonly Goal[],
  now: Date,
): Goal[] {
  const celebrated = readCelebrated(workspaceId)
  const { celebrate, markSilently } = goalsToCelebrate(goals, celebrated, now)
  if (celebrate.length === 0 && markSilently.length === 0) return []
  for (const id of [...celebrate, ...markSilently]) celebrated.add(id)
  writeCelebrated(workspaceId, celebrated)
  return goals.filter((goal) => celebrate.includes(goal.id))
}

export interface CelebratedGoal {
  id: string
  name: string
  icon: string
}

/** The celebrations on screen, each leaving by itself (see `GoalCelebration`). */
export function useCelebrationQueue() {
  const [items, setItems] = useState<CelebratedGoal[]>([])
  const add = useCallback((goals: readonly CelebratedGoal[]) => {
    setItems((current) => [
      ...current,
      ...goals
        .filter((goal) => !current.some((each) => each.id === goal.id))
        .map(({ id, name, icon }) => ({ id, name, icon })),
    ])
  }, [])
  const remove = useCallback(
    (id: string) => setItems((current) => current.filter((each) => each.id !== id)),
    [],
  )
  return { items, add, remove }
}

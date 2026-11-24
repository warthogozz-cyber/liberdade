import { lastLocalActionAt } from '@/shared/events'
import { playAchievement } from './playAchievement'
import { isSoundEnabled } from './soundPreference'

const WINDOW_MS = 10_000

/**
 * Plays the achievement sound when it is on and the person did something on this device in the
 * last 10 s. A celebration born from a live refresh stays silent.
 */
export function celebrationSound(workspaceId: string, now: number = Date.now()): void {
  if (!isSoundEnabled()) return
  const last = lastLocalActionAt(workspaceId)
  if (last === null || now - last > WINDOW_MS) return
  void playAchievement()
}

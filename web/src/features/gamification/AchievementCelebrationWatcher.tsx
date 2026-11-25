import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useState } from 'react'
import { useIntl } from 'react-intl'
import { useCurrentWorkspace } from '@/shared/api'
import { gamificationKeys, getAchievements, useAchievements } from '@/shared/api/gamification'
import { onLocalAction } from '@/shared/events'
import { registerMessages } from '@/shared/i18n'
import { Celebration, CelebrationStack } from '@/shared/motion'
import { celebrationSound } from '@/shared/sound'
import { achievementName } from './achievementCopy'
import { newlyEarned } from './celebrations'
import { gamificationMessages } from './messages.pt-BR'

registerMessages(gamificationMessages)

interface Shown {
  id: number
  key: Parameters<typeof achievementName>[1]
}

let nextId = 0

/**
 * Invisible until an achievement is earned: after a save made on this device, it reads the
 * achievements again and celebrates what is new against the list it held (D11). Queued saves, a
 * live notice, a failing call and a missing base stay silent.
 */
export default function AchievementCelebrationWatcher() {
  const intl = useIntl()
  const queryClient = useQueryClient()
  const ws = useCurrentWorkspace()?.id ?? ''
  // Keeps the list in the cache, which is the base of the next comparison.
  useAchievements(ws)
  const [shown, setShown] = useState<Shown[]>([])
  const remove = useCallback(
    (id: number) => setShown((current) => current.filter((each) => each.id !== id)),
    [],
  )

  useEffect(() => {
    if (ws === '') return
    // One at a time, so two saves in a row cannot both celebrate the same achievement.
    let queue: Promise<void> = Promise.resolve()
    return onLocalAction((action) => {
      if (action.fromQueue || action.workspaceId !== ws) return
      queue = queue
        .then(async () => {
          const key = gamificationKeys.achievements(ws)
          const before = queryClient.getQueryData<{ items: Parameters<typeof newlyEarned>[0] }>(key)
          const after = await queryClient.fetchQuery({
            queryKey: key,
            queryFn: () => getAchievements(ws),
            staleTime: 0,
          })
          // The streak moves with a save too.
          await queryClient.invalidateQueries({ queryKey: gamificationKeys.streak(ws) })
          if (!before) return
          const earned = newlyEarned(before.items, after.items, Date.now())
          if (earned.length === 0) return
          setShown((current) => [
            ...current,
            ...earned.map((each) => ({ id: nextId++, key: each })),
          ])
          celebrationSound(ws)
        })
        // A failed call (offline, the module off) only means no celebration this time.
        .catch(() => undefined)
    })
  }, [queryClient, ws])

  if (shown.length === 0) return null
  return (
    <CelebrationStack>
      {shown.map((item) => (
        <Celebration
          key={item.id}
          icon="🏆"
          message={intl.formatMessage(
            { id: 'gamification.celebrate' },
            { name: achievementName(intl, item.key) },
          )}
          onDone={() => remove(item.id)}
        />
      ))}
    </CelebrationStack>
  )
}

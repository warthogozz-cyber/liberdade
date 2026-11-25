import { lazy } from 'react'
import { registerModule, registerSlot } from '@/shared/modules'

// Imported for its effect by the app's routes, so the module is on offer. It stays this small on
// purpose: anything it imports lands in the first load. The pieces register their own text.
registerModule({ key: 'gamification', available: true })

registerSlot('home', {
  key: 'gamification-streak-card',
  module: 'gamification',
  roles: ['OWNER', 'ADULT', 'YOUTH'],
  Component: lazy(() => import('./StreakHomeCard')),
})

registerSlot('workspace', {
  key: 'gamification-achievement-celebration',
  module: 'gamification',
  roles: ['OWNER', 'ADULT', 'YOUTH'],
  Component: lazy(() => import('./AchievementCelebrationWatcher')),
})

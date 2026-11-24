import { useEffect } from 'react'
import { useIntl } from 'react-intl'
import type { CelebratedGoal } from './celebration'
import { goalEmoji } from './goalIcons'
import './register'

const SHOWN_MS = 6000

function prefersReducedMotion(): boolean {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
}

interface GoalCelebrationProps {
  name: string
  icon: string
  onDone: () => void
}

/**
 * A short, calm acknowledgement that a goal was reached: no sound and no confetti. It fades in for
 * under a second with `opacity` and `transform`; with `prefers-reduced-motion` the icon stands still
 * and the text is just "Meta atingida!" (D20). It leaves by itself.
 */
export function GoalCelebration({ name, icon, onDone }: GoalCelebrationProps) {
  const intl = useIntl()
  const still = prefersReducedMotion()
  useEffect(() => {
    const timer = setTimeout(onDone, SHOWN_MS)
    return () => clearTimeout(timer)
  }, [onDone])
  return (
    <div
      role="status"
      className={`pointer-events-auto flex items-center gap-3 rounded-card border border-primary bg-surface p-4 shadow-lg ${still ? '' : 'motion-safe:animate-goal-pop'}`}
    >
      <span aria-hidden="true" className="text-3xl">
        {goalEmoji(icon)}
      </span>
      <span className="font-medium">
        {still
          ? intl.formatMessage({ id: 'planning.goals.celebrationStill' })
          : intl.formatMessage({ id: 'planning.goals.celebration' }, { name })}
      </span>
    </div>
  )
}

interface GoalCelebrationsProps {
  items: readonly CelebratedGoal[]
  onDone: (id: string) => void
}

/** Where the celebrations appear, above the bottom bar and out of the way of the controls. */
export function GoalCelebrations({ items, onDone }: GoalCelebrationsProps) {
  if (items.length === 0) return null
  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-24 z-50 mx-auto flex max-w-lg flex-col gap-2 px-4">
      {items.map((item) => (
        <GoalCelebration
          key={item.id}
          name={item.name}
          icon={item.icon}
          onDone={() => onDone(item.id)}
        />
      ))}
    </div>
  )
}

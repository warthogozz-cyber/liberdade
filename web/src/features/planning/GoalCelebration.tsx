import { useIntl } from 'react-intl'
import { Celebration, CelebrationStack } from '@/shared/motion'
import type { CelebratedGoal } from './celebration'
import { goalEmoji } from './goalIcons'
import './register'

interface GoalCelebrationProps {
  name: string
  icon: string
  onDone: () => void
}

/** A goal was reached: the shared calm celebration with the goal's icon (D20). */
export function GoalCelebration({ name, icon, onDone }: GoalCelebrationProps) {
  const intl = useIntl()
  return (
    <Celebration
      icon={goalEmoji(icon)}
      message={intl.formatMessage({ id: 'planning.goals.celebration' }, { name })}
      onDone={onDone}
    />
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
    <CelebrationStack>
      {items.map((item) => (
        <GoalCelebration
          key={item.id}
          name={item.name}
          icon={item.icon}
          onDone={() => onDone(item.id)}
        />
      ))}
    </CelebrationStack>
  )
}

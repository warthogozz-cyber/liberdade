import { type ReactNode, useEffect, useRef } from 'react'
import { useReducedMotion } from './useReducedMotion'

interface CelebrationProps {
  icon: ReactNode
  message: string
  onDone: () => void
  shownMs?: number
}

/**
 * A short, calm acknowledgement: no sound and no confetti. It fades in with `opacity` and
 * `transform`; with reduced motion the icon stands still and the text stays whole (D20). It leaves
 * by itself. `onDone` lives in a ref, so a parent render with a new callback keeps the clock.
 */
export function Celebration({ icon, message, onDone, shownMs = 6000 }: CelebrationProps) {
  const reduced = useReducedMotion()
  const onDoneRef = useRef(onDone)
  useEffect(() => {
    onDoneRef.current = onDone
  })
  useEffect(() => {
    const timer = setTimeout(() => onDoneRef.current(), shownMs)
    return () => clearTimeout(timer)
  }, [shownMs])
  return (
    <div
      role="status"
      className={`pointer-events-auto flex items-center gap-3 rounded-card border border-primary bg-surface p-4 shadow-lg ${reduced ? '' : 'motion-safe:animate-goal-pop'}`}
    >
      <span aria-hidden="true" className="text-3xl">
        {icon}
      </span>
      <span className="font-medium">{message}</span>
    </div>
  )
}

/** Where the celebrations appear, above the bottom bar and out of the way of the controls. */
export function CelebrationStack({ children }: { children: ReactNode }) {
  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-24 z-50 mx-auto flex max-w-lg flex-col gap-2 px-4">
      {children}
    </div>
  )
}

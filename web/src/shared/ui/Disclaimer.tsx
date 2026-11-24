import type { ReactNode } from 'react'

/** A band pinned to the top of the screen: educational notices that must stay in view. */
export function Disclaimer({ children }: { children: ReactNode }) {
  return (
    <div
      role="note"
      className="sticky top-0 z-10 border-b border-text-muted/30 bg-surface px-4 py-2 text-sm text-text"
    >
      {children}
    </div>
  )
}

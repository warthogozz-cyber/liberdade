import { useEffect, useId, useRef, useState } from 'react'
import { useIntl } from 'react-intl'
import type { TourStep } from './catalog'
import { setHelpOpen } from './helpStore'

const button =
  'min-h-12 rounded-field border border-primary px-4 font-medium text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'

function findAnchor(anchor: string): HTMLElement | null {
  return document.querySelector<HTMLElement>(`[data-tour="${anchor}"]`)
}

function presentAnchors(steps: TourStep[]): string[] {
  return steps.filter((step) => findAnchor(step.anchor) !== null).map((step) => step.anchor)
}

const sameList = (a: string[], b: string[]) =>
  a.length === b.length && a.every((x, i) => x === b[i])

/**
 * The tour card. The anchors on the screen are watched live, so one that renders late joins the
 * tour; the current step is tracked by its anchor, so that never moves the person.
 */
export default function Tour({ steps, onClose }: { steps: TourStep[]; onClose: () => void }) {
  const intl = useIntl()
  const titleId = useId()
  const cardRef = useRef<HTMLDivElement>(null)
  const previousFocus = useRef<Element | null>(null)
  const [present, setPresent] = useState<string[]>(() => presentAnchors(steps))
  const [currentAnchor, setCurrentAnchor] = useState<string | null>(null)

  useEffect(() => {
    const update = () => {
      const next = presentAnchors(steps)
      setPresent((old) => (sameList(old, next) ? old : next))
    }
    update()
    const observer = new MutationObserver(update)
    observer.observe(document.body, { childList: true, subtree: true })
    return () => observer.disconnect()
  }, [steps])

  const open = present.length > 0

  useEffect(() => {
    if (!open) return
    setHelpOpen(true)
    return () => setHelpOpen(false)
  }, [open])
  // Pin the first step by its anchor, so an anchor that renders before it later does not move the person.
  if (open && currentAnchor === null) setCurrentAnchor(present[0] ?? null)
  const found = currentAnchor === null ? -1 : present.indexOf(currentAnchor)
  const index = found === -1 ? 0 : found
  const anchor = open ? present[index] : undefined
  const step = steps.find((candidate) => candidate.anchor === anchor)

  // Once, when the card first appears.
  useEffect(() => {
    if (!open) return
    previousFocus.current = document.activeElement
    cardRef.current?.focus()
  }, [open])

  useEffect(() => {
    if (!anchor) return
    const el = findAnchor(anchor)
    if (!el) return
    const previousOutline = el.style.outline
    const previousOffset = el.style.outlineOffset
    el.style.outline = '3px solid var(--color-primary)'
    el.style.outlineOffset = '2px'
    let reduced = false
    try {
      reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    } catch {
      // No media query support: scroll smoothly.
    }
    el.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'center' })
    return () => {
      el.style.outline = previousOutline
      el.style.outlineOffset = previousOffset
    }
  }, [anchor])

  const close = () => {
    const back = previousFocus.current
    onClose()
    if (back instanceof HTMLElement) back.focus()
  }
  const closeRef = useRef(close)
  useEffect(() => {
    closeRef.current = close
  })

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      // An Esc meant for another dialog (not the tour card) must not end the tour.
      const target = event.target
      const otherDialog = target instanceof Element ? target.closest('[role="dialog"]') : null
      if (otherDialog !== null && otherDialog !== cardRef.current) return
      closeRef.current()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])

  if (!open || !step) return null
  const last = index === present.length - 1

  return (
    <div
      ref={cardRef}
      role="dialog"
      aria-modal="false"
      aria-labelledby={titleId}
      tabIndex={-1}
      className="fixed inset-x-4 bottom-20 z-40 mx-auto flex max-w-lg flex-col gap-3 rounded-card border border-primary bg-surface p-4 text-text shadow-lg focus:outline-none"
    >
      <p className="text-sm text-text-muted">
        {intl.formatMessage({ id: 'tips.tour.step' }, { n: index + 1, total: present.length })}
      </p>
      <p id={titleId}>{intl.formatMessage({ id: step.messageId })}</p>
      <div className="flex items-center justify-between gap-2">
        <button type="button" onClick={close} className={button}>
          {intl.formatMessage({ id: 'tips.tour.skip' })}
        </button>
        <div className="flex gap-2">
          {index > 0 && (
            <button
              type="button"
              onClick={() => setCurrentAnchor(present[index - 1] ?? null)}
              className={button}
            >
              {intl.formatMessage({ id: 'tips.tour.back' })}
            </button>
          )}
          <button
            type="button"
            onClick={() => (last ? close() : setCurrentAnchor(present[index + 1] ?? null))}
            className={`${button} bg-primary text-on-primary`}
          >
            {intl.formatMessage({ id: last ? 'tips.tour.done' : 'tips.tour.next' })}
          </button>
        </div>
      </div>
    </div>
  )
}

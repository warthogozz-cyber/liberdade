import type { VentureFigures } from '@/shared/api/ventures'
import { venturesMessages as text } from './messages.pt-BR'

/**
 * "60.0" as the family reads it: "60,0%", and "−88,0%" with the Unicode minus. The API sends the
 * percent already rounded to one decimal, so the text never goes through `Number`.
 */
export function percentText(percent: string): string {
  const negative = percent.startsWith('-')
  const digits = negative ? percent.slice(1) : percent
  return `${negative ? '−' : ''}${digits.replace('.', ',')}%`
}

/** How long the investment takes to come back, or the neutral "does not pay back" text. */
export function paybackText(figures: VentureFigures): string {
  if (figures.paybackState === 'NEVER' || figures.paybackMonths === null) {
    return text['ventures.figures.paybackNever']
  }
  const months = figures.paybackMonths
  return `${months} ${months === 1 ? text['ventures.figures.month'] : text['ventures.figures.months']}`
}

/** The twelve-month return, or "no initial investment" when there is nothing to divide by. */
export function roiText(figures: VentureFigures): string {
  if (figures.roiState === 'NO_INVESTMENT' || figures.roi12mPercent === null) {
    return text['ventures.figures.roiNoInvestment']
  }
  return percentText(figures.roi12mPercent)
}

/** The neutral text for a break-even that cannot be shown as a number; undefined when it can. */
export function breakEvenText(figures: VentureFigures): string | undefined {
  switch (figures.breakEven.state) {
    case 'NO_BREAK_EVEN':
      return text['ventures.figures.noBreakEven']
    case 'NEEDS_PLANNED_UNITS':
      return text['ventures.figures.needsPlannedUnits']
    case 'NO_PRODUCTS':
      return text['ventures.figures.noProducts']
    case 'OK':
      return undefined
  }
}

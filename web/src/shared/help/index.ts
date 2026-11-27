import { lazy } from 'react'
export { PageTip } from './PageTip'
export { PageTour } from './PageTour'

// Kept out of the main chunk until an event tip really shows.
export const EventTip = lazy(() => import('./EventTip'))

export { eventTips, glossary, moduleHelp, seenId, tours } from './catalog'
export { OPEN_DIALOG_SELECTOR } from './dialogs'
export type { EventTipId, ModuleHelp, TipId, TourId } from './catalog'
export {
  clearSeen,
  hasSeen,
  isHelpOpen,
  isTipsEnabled,
  markSeen,
  resetAllSeen,
  setHelpOpen,
  setTipsEnabled,
  useSeen,
  useTipsEnabled,
} from './helpStore'

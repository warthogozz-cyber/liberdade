import type { ModuleKey } from '@/shared/api'
import type { helpMessages } from './messages.pt-BR'

export type TipId =
  | 'home'
  | 'launch'
  | 'dashboard'
  | 'transactions'
  | 'accounts'
  | 'calculator'
  | 'import'
  | 'portrait'
  | 'categories'
  | 'family'
  | 'modules'
  | 'budget'
  | 'goals'
  | 'recurrences'
  | 'receipt'
  | 'shopping-list'
  | 'products'
  | 'market'
  | 'compare'
  | 'venture-real'
export type EventTipId = 'offline' | 'live'
export type TourId = 'planning' | 'indicators' | 'achievements' | 'markets' | 'ventures'
export type HelpMessageId = keyof typeof helpMessages

export interface TourStep {
  /** The `data-tour` value of the element on the screen. */
  anchor: string
  messageId: HelpMessageId
}

/** What a route offers as help; a union, so a screen never has both a tip and a tour. */
export type RouteHelp =
  | { tip: TipId }
  | { tour: TourId }
  | { inline: string }
  | { inherits: string }
  | { exception: string }

export interface ModuleHelp {
  module: ModuleKey
  tour: TourId
  path: string
  titleId: string
  hiddenForYouth: boolean
}

/** The first visit tip of each screen that has one (the rest arrive with their screens). */
export const screenTips: Partial<Record<TipId, HelpMessageId>> = {
  dashboard: 'tips.dashboard',
  transactions: 'tips.transactions',
  accounts: 'tips.accounts',
  calculator: 'tips.calculator',
  import: 'tips.import',
}

export const eventTips: Partial<Record<EventTipId, HelpMessageId>> = {}

/** The steps of each tour, in order; a step whose anchor is not on the screen is skipped. */
export const tours: Partial<Record<TourId, TourStep[]>> = {
  planning: [
    { anchor: 'planning-budget', messageId: 'tips.tour.planning.budget' },
    { anchor: 'planning-goals', messageId: 'tips.tour.planning.goals' },
    { anchor: 'planning-reserve', messageId: 'tips.tour.planning.reserve' },
    { anchor: 'planning-recurring', messageId: 'tips.tour.planning.recurring' },
  ],
  indicators: [
    { anchor: 'indicators-cards', messageId: 'tips.tour.indicators.cards' },
    { anchor: 'indicators-calculator', messageId: 'tips.tour.indicators.calculator' },
  ],
  achievements: [
    { anchor: 'achievements-streak', messageId: 'tips.tour.achievements.streak' },
    { anchor: 'achievements-list', messageId: 'tips.tour.achievements.list' },
  ],
}

/**
 * The optional modules on the "Como funciona" screen, in the spec's order. `titleId` and the
 * body (`help.module.<module>`) are ids of that screen's own bundle.
 */
export const moduleHelp: ModuleHelp[] = [
  {
    module: 'planning',
    tour: 'planning',
    path: '/planejar',
    titleId: 'help.module.planning.title',
    hiddenForYouth: false,
  },
  {
    module: 'indicators',
    tour: 'indicators',
    path: '/mais/indicadores',
    titleId: 'help.module.indicators.title',
    hiddenForYouth: false,
  },
  {
    module: 'gamification',
    tour: 'achievements',
    path: '/mais/conquistas',
    titleId: 'help.module.gamification.title',
    hiddenForYouth: false,
  },
  {
    module: 'markets',
    tour: 'markets',
    path: '/mais/feira',
    titleId: 'help.module.markets.title',
    hiddenForYouth: false,
  },
  {
    module: 'ventures',
    tour: 'ventures',
    path: '/mais/projetos',
    titleId: 'help.module.ventures.title',
    hiddenForYouth: true,
  },
]

/** The words of the "Como funciona" screen, in order; ids of the screen's own bundle. */
export const glossary: { termId: string; bodyId: string }[] = [
  'passkey',
  'cdb',
  'lciLca',
  'cdi',
  'ipca',
  'selic',
  'fgc',
].map((word) => ({ termId: `help.term.${word}`, bodyId: `help.def.${word}` }))

/** Key = full path, e.g. '/mais/feira/lista'. */
export const routeHelp: Record<string, RouteHelp> = {
  '/mais/como-funciona': { exception: 'help screen' },
  '/mais/ajustes': { inline: 'settings.motion' },
}

/** The ids under which "seen" is stored; they match what existing devices already hold. */
export const seenId = {
  tip: (id: TipId): string => id,
  tour: (id: TourId): string => `tour-${id}`,
  event: (id: EventTipId): string => `event-${id}`,
}

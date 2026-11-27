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

/** The first visit tip of each screen that has one. */
export const screenTips: Record<TipId, HelpMessageId> = {
  home: 'tips.home',
  launch: 'tips.launch',
  dashboard: 'tips.dashboard',
  transactions: 'tips.transactions',
  accounts: 'tips.accounts',
  calculator: 'tips.calculator',
  import: 'tips.import',
  portrait: 'tips.portrait',
  categories: 'tips.categories',
  family: 'tips.family',
  modules: 'tips.modules',
  budget: 'tips.budget',
  goals: 'tips.goals',
  recurrences: 'tips.recurrences',
  receipt: 'tips.receipt',
  'shopping-list': 'tips.shoppingList',
  products: 'tips.products',
  market: 'tips.market',
  compare: 'tips.compare',
  'venture-real': 'tips.ventureReal',
}

export const eventTips: Record<EventTipId, HelpMessageId> = {
  offline: 'tips.event.offline',
  live: 'tips.event.live',
}

/** The steps of each tour, in order; a step whose anchor is not on the screen is skipped. */
export const tours: Record<TourId, TourStep[]> = {
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
    { anchor: 'achievements-rest', messageId: 'tips.tour.achievements.streakBreak' },
    { anchor: 'achievements-list', messageId: 'tips.tour.achievements.list' },
  ],
  markets: [
    { anchor: 'markets-nearby', messageId: 'tips.tour.markets.nearby' },
    { anchor: 'markets-receipt', messageId: 'tips.tour.markets.receipt' },
    { anchor: 'markets-list', messageId: 'tips.tour.markets.list' },
  ],
  ventures: [
    { anchor: 'ventures-intro', messageId: 'tips.tour.ventures.intro' },
    { anchor: 'ventures-start', messageId: 'tips.tour.ventures.start' },
    { anchor: 'ventures-actual', messageId: 'tips.tour.ventures.actual' },
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
  '/inicio': { tip: 'home' },
  '/lancar': { tip: 'launch' },
  '/painel': { tip: 'dashboard' },
  '/painel/retrato': { tip: 'portrait' },
  '/lancamentos': { tip: 'transactions' },
  '/lancamentos/importar': { tip: 'import' },
  '/mais/contas': { tip: 'accounts' },
  '/mais/categorias': { tip: 'categories' },
  '/mais/modulos': { tip: 'modules' },
  '/mais/indicadores/calculadora': { tip: 'calculator' },
  '/mais/privacidade': { inline: 'privacy.intro' },
  '/planejar/orcamento': { tip: 'budget' },
  '/planejar/metas': { tip: 'goals' },
  '/planejar/recorrencias': { tip: 'recurrences' },
  '/mais/feira/nota': { tip: 'receipt' },
  '/mais/feira/lista': { tip: 'shopping-list' },
  '/mais/feira/produtos': { tip: 'products' },
  '/mais/feira/mercado/:ref': { tip: 'market' },
  '/mais/feira/comparar': { tip: 'compare' },
  '/mais/projetos/:id/real': { tip: 'venture-real' },
  '/mais/familia': { tip: 'family' },
  '/mais/familia/convidar': { inherits: '/mais/familia' },
  '/mais/como-funciona': { exception: 'help screen' },
  '/mais/ajustes': { inline: 'settings.motion' },
  '/mais/feira': { tour: 'markets' },
  '/mais/projetos': { tour: 'ventures' },
  '/mais/projetos/comecar': { inherits: '/mais/projetos' },
  '/mais/projetos/:id/editar': { inherits: '/mais/projetos' },
  '/mais/projetos/:id': { inline: 'ventures.caption.margin' },
  '/mais/projetos/investimentos': { inline: 'ventures.invest.intro' },
  '/mais/projetos/investimentos/simulador': { inline: 'ventures.invest.disclaimer' },
}

/** The ids under which "seen" is stored; they match what existing devices already hold. */
export const seenId = {
  tip: (id: TipId): string => id,
  tour: (id: TourId): string => `tour-${id}`,
  event: (id: EventTipId): string => `event-${id}`,
}

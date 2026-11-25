import type { tipsMessages } from './messages.pt-BR'

/** The screens that have a tour. */
export type TourId = 'planning' | 'indicators' | 'achievements'

export interface TourStep {
  /** The `data-tour` value of the element on the screen. */
  anchor: string
  messageId: keyof typeof tipsMessages
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
    { anchor: 'achievements-list', messageId: 'tips.tour.achievements.list' },
  ],
}

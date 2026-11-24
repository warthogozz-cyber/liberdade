/** The icon keys the API accepts for a goal (D15), each with the emoji that stands for it. */
export const GOAL_ICONS = {
  savings: '🐷',
  home: '🏠',
  car: '🚗',
  travel: '✈️',
  education: '🎓',
  health: '🩺',
  gift: '🎁',
  party: '🎉',
  tech: '💻',
  other: '⭐',
} as const

export type GoalIconKey = keyof typeof GOAL_ICONS

export const GOAL_ICON_KEYS = Object.keys(GOAL_ICONS) as GoalIconKey[]

/** The emoji of a key; one this version does not know falls back to `other`. */
export function goalEmoji(key: string): string {
  return key in GOAL_ICONS ? GOAL_ICONS[key as GoalIconKey] : GOAL_ICONS.other
}

import type { CategoryColor } from '@/shared/api'

/**
 * The dot of each color, in the palette's order. Kept apart from `categoryStyle` because the chip
 * of `CategoryPicker` is in the first load and needs only these classes. Each class has a contrast
 * of 3:1 or more with `surface`, in the light and the dark theme.
 */
export const COLOR_DOT: Record<CategoryColor, string> = {
  TEAL: 'bg-teal-600',
  BLUE: 'bg-blue-500',
  INDIGO: 'bg-indigo-600 dark:bg-indigo-400',
  PURPLE: 'bg-purple-500',
  PINK: 'bg-pink-500',
  RED: 'bg-red-500',
  ORANGE: 'bg-orange-600',
  GREEN: 'bg-green-600',
}

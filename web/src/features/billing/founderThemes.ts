import type { FounderTheme } from '@/shared/api/billing'

/** The Founder accents, all at least 4.5:1 against white. TEAL is the default. */
export const founderThemes: Record<FounderTheme, { accent: string; label: string }> = {
  TEAL: { accent: '#0e5a66', label: 'Verde-azulado' },
  OCEAN: { accent: '#1f4e8c', label: 'Oceano' },
  FOREST: { accent: '#2f6b3a', label: 'Floresta' },
  PLUM: { accent: '#6b3a6b', label: 'Ameixa' },
}

export const THEME_ORDER: FounderTheme[] = ['TEAL', 'OCEAN', 'FOREST', 'PLUM']

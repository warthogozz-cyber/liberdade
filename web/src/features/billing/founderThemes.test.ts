import { describe, expect, it } from 'vitest'
import { founderThemes } from './founderThemes'

function channel(hex: string, start: number): number {
  const value = parseInt(hex.slice(start, start + 2), 16) / 255
  return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
}

function luminance(hex: string): number {
  return 0.2126 * channel(hex, 1) + 0.7152 * channel(hex, 3) + 0.0722 * channel(hex, 5)
}

function contrastWithWhite(hex: string): number {
  return 1.05 / (luminance(hex) + 0.05)
}

describe('founderThemes', () => {
  it('everyThemeHasAAContrast', () => {
    expect(Object.keys(founderThemes).sort()).toEqual(['FOREST', 'OCEAN', 'PLUM', 'TEAL'])
    for (const { accent } of Object.values(founderThemes)) {
      expect(contrastWithWhite(accent)).toBeGreaterThanOrEqual(4.5)
    }
  })
})

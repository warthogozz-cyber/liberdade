import { describe, expect, it } from 'vitest'
import {
  LIGHT_TOKENS,
  PORTRAIT_HEIGHT,
  PORTRAIT_WIDTH,
  drawPortrait,
  type PortraitBlock,
} from './drawPortrait'
import type { PortraitData } from './portraitData'
import indexCss from '../../index.css?raw'

const DATA: PortraitData = {
  month: 'novembro de 2026',
  slices: [
    { label: 'Mercado', percent: 60, other: false },
    { label: 'Outras', percent: 40, other: true },
  ],
  income: { kind: 'left', percent: 30 },
  empty: false,
}

function recorder() {
  const texts: string[] = []
  const ctx = {
    fillText: (text: string) => texts.push(text),
    measureText: (text: string) => ({ width: text.length * 10 }),
    fillRect: () => {},
    clearRect: () => {},
  } as unknown as CanvasRenderingContext2D
  return { ctx, texts }
}

function draw(
  data: PortraitData,
  options: { hidden?: PortraitBlock[]; founder?: boolean } = {},
): string[] {
  const { ctx, texts } = recorder()
  drawPortrait(ctx, data, {
    hidden: new Set(options.hidden ?? []),
    theme: options.founder ? 'OCEAN' : null,
    founder: options.founder ?? false,
    tokens: LIGHT_TOKENS,
  })
  return texts
}

function channel(hex: string, start: number): number {
  const value = parseInt(hex.slice(start, start + 2), 16) / 255
  return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
}
const luminance = (hex: string) =>
  0.2126 * channel(hex, 1) + 0.7152 * channel(hex, 3) + 0.0722 * channel(hex, 5)
const contrast = (a: string, b: string) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

describe('drawPortrait', () => {
  it('isOneThousandEightyByThirteenFifty', () => {
    expect([PORTRAIT_WIDTH, PORTRAIT_HEIGHT]).toEqual([1080, 1350])
  })

  it('drawsEveryBlockByDefault', () => {
    const texts = draw(DATA)
    expect(texts).toEqual(
      expect.arrayContaining(['novembro de 2026', 'Mercado', '60%', 'Sobrou 30% da renda']),
    )
  })

  it('hidesMonthIncomeAndCategories', () => {
    const month = draw(DATA, { hidden: ['month'] })
    expect(month).not.toContain('novembro de 2026')
    expect(month).toContain('Mercado')
    const income = draw(DATA, { hidden: ['income'] })
    expect(income.some((text) => text.startsWith('Sobrou'))).toBe(false)
    const categories = draw(DATA, { hidden: ['categories'] })
    expect(categories).not.toContain('Mercado')
    expect(categories).not.toContain('60%')
  })

  it('badgeOnlyForFounder', () => {
    expect(draw(DATA, { founder: true })).toContain('★ Família Fundadora')
    expect(draw(DATA)).not.toContain('★ Família Fundadora')
  })

  it('neverDrawsAnAmountInReais', () => {
    const texts = draw(DATA, { founder: true })
    expect(texts.every((text) => !/R\$|\d+,\d{2}/.test(text))).toBe(true)
  })

  it('footerUsesTheHost', () => {
    const footer = draw(DATA).find((text) => text.startsWith('Feito com Liberdade'))
    expect(footer).toContain(window.location.host)
  })

  it('cutsALongNameWithAnEllipsis', () => {
    const long = { ...DATA, slices: [{ label: 'x'.repeat(80), percent: 100, other: false }] }
    const name = draw(long).find((text) => text.startsWith('xx'))!
    expect(name.endsWith('…')).toBe(true)
    expect(name.length).toBeLessThan(80)
  })

  it('lightTokensAreReadableOnTheirBackground', () => {
    expect(contrast(LIGHT_TOKENS.text, LIGHT_TOKENS.bg)).toBeGreaterThanOrEqual(4.5)
    expect(contrast(LIGHT_TOKENS.primary, LIGHT_TOKENS.bg)).toBeGreaterThanOrEqual(4.5)
  })

  it('lightTokensMatchTheLightTheme', () => {
    // The first @theme block is the light one; the card copies it instead of reading the page.
    const start = indexCss.indexOf('{', indexCss.indexOf('@theme')) + 1
    const light = indexCss.slice(start, indexCss.indexOf('}', start))
    const token = (name: string) =>
      light
        .split(';')
        .map((line) => line.trim())
        .find((line) => line.startsWith(`--${name}:`))
        ?.slice(name.length + 3)
        .trim()

    expect(token('color-primary')).toBe(LIGHT_TOKENS.primary)
    expect(token('color-text')).toBe(LIGHT_TOKENS.text)
    expect(token('color-bg')).toBe(LIGHT_TOKENS.bg)
    expect(token('font-sans')).toBe(LIGHT_TOKENS.font)
  })
})

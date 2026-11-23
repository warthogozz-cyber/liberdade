import { CHART_PALETTE, OTHERS_COLOR } from '@/features/dashboard/charts'
import { founderThemes } from '@/features/billing/founderThemes'
import type { FounderTheme } from '@/shared/api/billing'
import type { PortraitData } from './portraitData'

export const PORTRAIT_WIDTH = 1080
export const PORTRAIT_HEIGHT = 1350

export type DesignTokens = { primary: string; text: string; bg: string; font: string }
export type PortraitBlock = 'categories' | 'income' | 'month'

const MARGIN = 80

/** The design system's colors and font, as the page uses them now (light or dark). */
export function readTokens(): DesignTokens {
  const style = getComputedStyle(document.documentElement)
  const read = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback
  return {
    primary: read('--color-primary', '#0e5a66'),
    text: read('--color-text', '#0b3b45'),
    bg: read('--color-bg', '#f4f8f8'),
    font: read('--font-sans', "'Inter Variable', system-ui, sans-serif"),
  }
}

/**
 * Draws the month card at 1080 x 1350. Percentages, category names, the month and the footer only:
 * never a person's name and never an amount in reais.
 */
export function drawPortrait(
  ctx: CanvasRenderingContext2D,
  data: PortraitData,
  options: {
    hidden: Set<PortraitBlock>
    theme: FounderTheme | null
    founder: boolean
    tokens: DesignTokens
  },
): void {
  const { hidden, tokens, founder } = options
  const accent = founder ? founderThemes[options.theme ?? 'TEAL'].accent : tokens.primary
  const font = (weight: number, size: number) => `${weight} ${size}px ${tokens.font}`

  ctx.clearRect(0, 0, PORTRAIT_WIDTH, PORTRAIT_HEIGHT)
  ctx.fillStyle = tokens.bg
  ctx.fillRect(0, 0, PORTRAIT_WIDTH, PORTRAIT_HEIGHT)
  ctx.fillStyle = accent
  ctx.fillRect(0, 0, PORTRAIT_WIDTH, 24)
  ctx.textBaseline = 'alphabetic'
  ctx.textAlign = 'left'

  let y = 150
  ctx.fillStyle = accent
  ctx.font = font(700, 56)
  ctx.fillText('Retrato do mês', MARGIN, y)
  if (founder) {
    ctx.font = font(600, 34)
    ctx.textAlign = 'right'
    ctx.fillText('★ Família Fundadora', PORTRAIT_WIDTH - MARGIN, y)
    ctx.textAlign = 'left'
  }
  if (!hidden.has('month')) {
    y += 70
    ctx.fillStyle = tokens.text
    ctx.font = font(500, 44)
    ctx.fillText(data.month, MARGIN, y)
  }
  y += 90

  if (data.empty) {
    ctx.fillStyle = tokens.text
    ctx.font = font(500, 40)
    ctx.fillText('Ainda não há gastos neste mês', MARGIN, y)
  } else if (!hidden.has('categories')) {
    const barWidth = PORTRAIT_WIDTH - MARGIN * 2
    data.slices.forEach((slice, index) => {
      ctx.fillStyle = tokens.text
      ctx.font = font(500, 38)
      ctx.textAlign = 'left'
      ctx.fillText(slice.label, MARGIN, y, barWidth - 160)
      ctx.font = font(700, 38)
      ctx.textAlign = 'right'
      ctx.fillText(`${slice.percent}%`, PORTRAIT_WIDTH - MARGIN, y)
      ctx.textAlign = 'left'
      ctx.globalAlpha = 0.15
      ctx.fillStyle = tokens.text
      ctx.fillRect(MARGIN, y + 20, barWidth, 28)
      ctx.globalAlpha = 1
      ctx.fillStyle = slice.other ? OTHERS_COLOR : CHART_PALETTE[index % CHART_PALETTE.length]
      ctx.fillRect(MARGIN, y + 20, (barWidth * slice.percent) / 100, 28)
      y += 120
    })
  }

  if (data.income && !hidden.has('income')) {
    y += 20
    ctx.fillStyle = accent
    ctx.fillRect(MARGIN, y - 20, PORTRAIT_WIDTH - MARGIN * 2, 4)
    y += 70
    ctx.fillStyle = tokens.text
    ctx.font = font(700, 48)
    ctx.fillText(
      data.income.kind === 'left'
        ? `Sobrou ${data.income.percent}% da renda`
        : `Gastou ${data.income.percent}% a mais que a renda`,
      MARGIN,
      y,
      PORTRAIT_WIDTH - MARGIN * 2,
    )
  }

  ctx.fillStyle = tokens.text
  ctx.globalAlpha = 0.7
  ctx.font = font(500, 30)
  ctx.textAlign = 'left'
  ctx.fillText(`Feito com Liberdade · ${window.location.host}`, MARGIN, PORTRAIT_HEIGHT - 70)
  ctx.globalAlpha = 1
}

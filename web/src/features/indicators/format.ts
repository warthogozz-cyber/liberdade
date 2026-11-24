import type { components } from '@/shared/api/schema'
import { locale } from '@/shared/i18n'

type IndicatorView = components['schemas']['IndicatorView']
type VariationView = components['schemas']['VariationView']

const MINUS = '−'

/** The series the Banco Central publishes once a month; every other one is daily. */
const MONTHLY_CODES = new Set(['IPCA_MONTH', 'IPCA_12M'])

export function isMonthly(code: string): boolean {
  return MONTHLY_CODES.has(code)
}

/**
 * The server's plain decimal text with a comma and a real minus sign. It is text all the way: the
 * value never goes through `Number`, so nothing is rounded here.
 */
export function formatDecimal(text: string): string {
  return text.replace('.', ',').replace(/^-/, MINUS)
}

// Keyed by the unit the API sends. The BRL unit is the dollar quote's, in reais: it is the series'
// own unit, not the family's currency.
const VALUE_BY_UNIT: Record<string, (text: string) => string> = {
  PERCENT_YEAR: (text) => `${text}% a.a.`,
  PERCENT_MONTH: (text) => `${text}% no mês`,
  PERCENT_12M: (text) => `${text}% em 12 meses`,
  BRL: (text) => `R$ ${text}`,
}

/** "15,00% a.a.", "R$ 5,4100" and so on; null when the series has no point yet. */
export function formatIndicatorValue(view: IndicatorView): string | null {
  if (view.value === null) return null
  const text = formatDecimal(view.value)
  return VALUE_BY_UNIT[view.unit]?.(text) ?? text
}

/** "2026-11-05" as "05/11". */
function dayAndMonth(date: string): string {
  const [, month, day] = date.split('-')
  return `${day}/${month}`
}

/** "2026-10-01" as "outubro de 2026", the way the card writes the reference of a monthly series. */
function monthAndYear(date: string): string {
  return new Intl.DateTimeFormat(locale, {
    timeZone: 'UTC',
    month: 'long',
    year: 'numeric',
  }).format(new Date(`${date}T00:00:00Z`))
}

/** "+0,50 p.p. em 05/11": positive gets a plus sign, zero has none. Neutral text, no colour. */
export function formatVariation(variation: VariationView, code?: string): string {
  const zero = /^-?0+(\.0+)?$/.test(variation.value)
  const signed = zero
    ? formatDecimal(variation.value.replace('-', ''))
    : variation.value.startsWith('-')
      ? formatDecimal(variation.value)
      : `+${formatDecimal(variation.value)}`
  const amount = variation.unit === 'PP' ? `${signed} p.p.` : `${signed}%`
  const when =
    code !== undefined && isMonthly(code)
      ? monthAndYear(variation.since)
      : dayAndMonth(variation.since)
  return variation.basis === 'LAST_CHANGE'
    ? `${amount} em ${when}`
    : `${amount} em relação a ${when}`
}

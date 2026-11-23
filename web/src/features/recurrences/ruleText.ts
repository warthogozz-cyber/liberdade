import { createIntl, createIntlCache } from 'react-intl'
import type { RecurringRuleView } from '@/shared/api'
import { DEFAULT_LOCALE } from '@/shared/money'
import { recurrencesMessages } from './messages.pt-BR'

const cache = createIntlCache()
// Plain text by id: this bundle is read here without the app's typed ids.
const messages: Record<string, string> = recurrencesMessages

/**
 * One line that says when a rule repeats, from its frequency, interval and start: "Todo mês, dia
 * 10", "A cada 2 semanas, segunda" or "Todo ano, 29/02".
 */
export function ruleText(
  { frequency, interval, startOn }: Pick<RecurringRuleView, 'frequency' | 'interval' | 'startOn'>,
  locale = DEFAULT_LOCALE,
): string {
  const intl = createIntl({ locale, messages }, cache)
  const start = `${startOn}T00:00:00Z`
  const values = {
    interval,
    day: Number(startOn.slice(8, 10)),
    date: intl.formatDate(start, { day: '2-digit', month: '2-digit', timeZone: 'UTC' }),
    // "segunda-feira" reads as "segunda" here.
    weekday: intl.formatDate(start, { weekday: 'long', timeZone: 'UTC' }).replace(/-feira$/, ''),
  }
  return intl.formatMessage({ id: `recurrences.rule.${frequency}` as const }, values)
}

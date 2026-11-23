import { useIntl } from 'react-intl'
import { MIN_MONTH, addMonths } from '@/shared/i18n'

interface MonthSwitcherProps {
  /** The month shown, `YYYY-MM`. */
  month: string
  onChange: (month: string) => void
  /** The first month it can go to. */
  min?: string
  /** The last month it can go to. */
  max: string
}

const arrow =
  'flex size-12 items-center justify-center rounded-field text-2xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:opacity-40'

/** "‹ Novembro de 2026 ›": steps through the months, stopping at `min` and `max`. */
export function MonthSwitcher({ month, onChange, min = MIN_MONTH, max }: MonthSwitcherProps) {
  const intl = useIntl()
  const written = intl.formatDate(`${month}-01T00:00:00Z`, {
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  })
  // Some locales (pt-BR) write the month in lower case; a heading-like label starts with a capital.
  const name = written.charAt(0).toLocaleUpperCase(intl.locale) + written.slice(1)

  return (
    <div className="flex items-center justify-between gap-2">
      <button
        type="button"
        aria-label={intl.formatMessage({ id: 'month.previous' })}
        disabled={month <= min}
        onClick={() => onChange(addMonths(month, -1))}
        className={arrow}
      >
        <span aria-hidden="true">‹</span>
      </button>
      <span aria-live="polite" className="font-semibold">
        {name}
      </span>
      <button
        type="button"
        aria-label={intl.formatMessage({ id: 'month.next' })}
        disabled={month >= max}
        onClick={() => onChange(addMonths(month, 1))}
        className={arrow}
      >
        <span aria-hidden="true">›</span>
      </button>
    </div>
  )
}

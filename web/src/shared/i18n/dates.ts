/** The family's time zone until the API says otherwise. */
export const DEFAULT_TIMEZONE = 'America/Fortaleza'

/**
 * The calendar date (`YYYY-MM-DD`) at `now` in `timeZone`. It is put together from the formatter's
 * parts, so it does not depend on how any locale writes a date.
 */
export function localDate(now: Date, timeZone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now)
  const { year, month, day } = Object.fromEntries(parts.map(({ type, value }) => [type, value]))
  return `${year}-${month}-${day}`
}

/** The month (`YYYY-MM`) at `now` in `timeZone`. */
export function currentMonth(now: Date, timeZone: string): string {
  return localDate(now, timeZone).slice(0, 7)
}

/** Moves a `YYYY-MM-DD` date by whole days, on the calendar alone, with no time zone. */
export function addDays(date: string, days: number): string {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10)
}

/** The first day a transaction can have, as the API's `TransactionDateRange`. */
export const MIN_DATE = '2000-01-01'

/** The first month the list and the dashboard can show. */
export const MIN_MONTH = '2000-01'

/**
 * The last day a transaction can have: 366 days after today in the family's time zone, as the
 * API's `TransactionDateRange` works it out (Review Focus 4).
 */
export function maxDate(now: Date, timeZone: string): string {
  return addDays(localDate(now, timeZone), 366)
}

/** The month (`YYYY-MM`) of `maxDate`, the last one the list and the dashboard can show. */
export function maxMonth(now: Date, timeZone: string): string {
  return maxDate(now, timeZone).slice(0, 7)
}

/** Moves a `YYYY-MM` month by whole months. */
export function addMonths(month: string, months: number): string {
  const [year, monthNumber] = month.split('-').map(Number)
  const index = year * 12 + (monthNumber - 1) + months
  const nextYear = Math.floor(index / 12)
  const nextMonth = (index % 12) + 1
  return `${String(nextYear).padStart(4, '0')}-${String(nextMonth).padStart(2, '0')}`
}

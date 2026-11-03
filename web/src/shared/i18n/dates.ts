/** The family's time zone until the API says otherwise. */
export const DEFAULT_TIMEZONE = 'America/Fortaleza'

/** The calendar date (`YYYY-MM-DD`) at `now` in `timeZone`; en-CA already formats it that way. */
export function localDate(now: Date, timeZone: string): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now)
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

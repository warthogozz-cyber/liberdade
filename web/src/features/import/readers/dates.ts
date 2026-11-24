/** The day as yyyy-MM-dd when it exists on the calendar (no 31 February), or null. */
export function calendarDate(year: string, month: string, day: string): string | null {
  const y = Number(year)
  const m = Number(month)
  const d = Number(day)
  const probe = new Date(Date.UTC(y, m - 1, d))
  const real =
    year.length === 4 &&
    probe.getUTCFullYear() === y &&
    probe.getUTCMonth() === m - 1 &&
    probe.getUTCDate() === d
  return real ? `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}` : null
}

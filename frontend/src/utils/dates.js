/** Today's date as YYYY-MM-DD in the given IANA timezone (matches the backend's LocalDate). */
export function todayIn(timeZone) {
  try {
    return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(
      new Date(),
    )
  } catch {
    return new Date().toISOString().slice(0, 10)
  }
}

/** "2026-11" -> "November 2026" */
export function longMonth(yearMonth) {
  const [y, m] = yearMonth.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, 1)).toLocaleString('en-IN', { month: 'long', year: 'numeric', timeZone: 'UTC' })
}

/** "2026-11", +1 -> "2026-12" (handles year boundaries) */
export function shiftMonth(yearMonth, delta) {
  const [y, m] = yearMonth.split('-').map(Number)
  const d = new Date(Date.UTC(y, m - 1 + delta, 1))
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`
}

/** Adds days to a YYYY-MM-DD string without timezone surprises. */
export function addDays(isoDate, days) {
  const d = new Date(`${isoDate}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

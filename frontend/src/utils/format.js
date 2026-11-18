const inrFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/** 123456.5 -> "₹1,23,456.50" (Indian digit grouping). */
export function formatInr(amount) {
  if (amount === null || amount === undefined || amount === '') return '—'
  const value = Number(amount)
  if (Number.isNaN(value)) return '—'
  return inrFormatter.format(value)
}

const dateFormatter = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
})

/** "2026-11" (API month) -> "Nov 26". */
export function monthLabel(yearMonth) {
  const [y, m] = yearMonth.split('-').map(Number)
  const name = new Date(Date.UTC(y, m - 1, 1)).toLocaleString('en-IN', { month: 'short', timeZone: 'UTC' })
  return `${name} ${String(y).slice(2)}`
}

/** "2026-12-05" (API LocalDate) -> "05 Dec 2026". */
export function formatDate(isoDate) {
  if (!isoDate) return '—'
  const date = new Date(`${isoDate}T00:00:00Z`)
  if (Number.isNaN(date.getTime())) return '—'
  return dateFormatter.format(date)
}

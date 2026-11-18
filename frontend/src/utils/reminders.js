/**
 * Parses "30, 15, 7" into [30, 15, 7] (unique, sorted high to low).
 * Returns { days } or { error }.
 */
export function parseReminderDays(text) {
  const parts = text
    .split(/[\s,]+/)
    .map((p) => p.trim())
    .filter(Boolean)
  const days = []
  for (const p of parts) {
    if (!/^\d+$/.test(p)) return { error: `"${p}" isn't a whole number of days` }
    const n = Number(p)
    if (n < 1 || n > 90) return { error: 'Reminder days must be between 1 and 90' }
    if (!days.includes(n)) days.push(n)
  }
  if (days.length > 10) return { error: 'At most 10 reminders' }
  return { days: days.sort((a, b) => b - a) }
}

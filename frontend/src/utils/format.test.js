import { formatDate, formatInr, monthLabel } from './format'

describe('monthLabel', () => {
  it('shortens API months', () => {
    expect(monthLabel('2026-11')).toBe('Nov 26')
    expect(monthLabel('2027-01')).toBe('Jan 27')
  })
})

describe('formatInr', () => {
  it('uses Indian digit grouping with two decimals', () => {
    expect(formatInr(123456.5)).toBe('₹1,23,456.50')
    expect(formatInr(649)).toBe('₹649.00')
    expect(formatInr('10000000')).toBe('₹1,00,00,000.00')
  })

  it('returns a dash for empty or invalid values', () => {
    expect(formatInr(null)).toBe('—')
    expect(formatInr(undefined)).toBe('—')
    expect(formatInr('abc')).toBe('—')
  })
})

describe('formatDate', () => {
  it('formats API dates without timezone shift', () => {
    expect(formatDate('2026-12-05')).toBe('05 Dec 2026')
    expect(formatDate('2027-01-31')).toBe('31 Jan 2027')
  })

  it('returns a dash for empty or invalid values', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDate('not-a-date')).toBe('—')
  })
})

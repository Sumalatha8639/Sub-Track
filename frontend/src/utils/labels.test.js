import { daysLeftText } from './labels'
import { parseReminderDays } from './reminders'

describe('daysLeftText', () => {
  it.each([
    [10, '10 days left'],
    [1, 'Due tomorrow'],
    [0, 'Due today'],
    [-1, 'Overdue by 1 day'],
    [-5, 'Overdue by 5 days'],
  ])('%i -> %s', (days, text) => {
    expect(daysLeftText(days)).toBe(text)
  })
})

describe('parseReminderDays', () => {
  it('parses, de-duplicates and sorts', () => {
    expect(parseReminderDays('7, 30 3,7')).toEqual({ days: [30, 7, 3] })
  })

  it('allows empty (reminders off)', () => {
    expect(parseReminderDays('  ')).toEqual({ days: [] })
  })

  it('rejects out of range and non-numbers', () => {
    expect(parseReminderDays('0').error).toMatch(/between 1 and 90/)
    expect(parseReminderDays('91').error).toMatch(/between 1 and 90/)
    expect(parseReminderDays('7, soon').error).toMatch(/whole number/)
  })
})

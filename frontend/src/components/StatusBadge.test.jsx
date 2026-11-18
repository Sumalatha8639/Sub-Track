import { render, screen } from '@testing-library/react'
import StatusBadge from './StatusBadge.jsx'

describe('StatusBadge', () => {
  it.each([
    ['PAID', 'Paid', 'bg-emerald-100'],
    ['UPCOMING', 'Upcoming', 'bg-sky-100'],
    ['DUE_SOON', 'Due soon', 'bg-yellow-100'],
    ['DUE_TODAY', 'Due today', 'bg-amber-100'],
    ['AWAITING_CONFIRMATION', 'Awaiting confirmation', 'bg-orange-100'],
    ['OVERDUE', 'Overdue', 'bg-rose-100'],
    ['MISSED', 'Missed', 'bg-slate-800'],
  ])('payment status %s shows "%s"', (status, label, colour) => {
    render(<StatusBadge status={status} />)
    expect(screen.getByText(label)).toHaveClass(colour)
  })

  it('renders subscription statuses', () => {
    render(<StatusBadge kind="subscription" status="PAUSED" />)
    expect(screen.getByText('Paused')).toBeInTheDocument()
  })

  it('falls back to the raw value for unknown statuses', () => {
    render(<StatusBadge status="SOMETHING_NEW" />)
    expect(screen.getByText('SOMETHING_NEW')).toBeInTheDocument()
  })
})

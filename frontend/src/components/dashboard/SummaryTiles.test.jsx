import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SummaryTiles from './SummaryTiles.jsx'

const summary = {
  activeCount: 4,
  dueIn30DaysCount: 2,
  dueIn30DaysAmount: 768,
  overdueCount: 2,
  overdueAmount: 3499,
  awaitingConfirmationCount: 1,
  paidThisMonthCount: 1,
  paidThisMonthAmount: 649,
  missedThisMonthCount: 1,
  monthlyCommitment: 2767,
  yearlyCommitment: 33204,
}

describe('SummaryTiles', () => {
  it('shows the four KPIs with rupee amounts', () => {
    render(<SummaryTiles summary={summary} onPick={() => {}} />)
    expect(screen.getByText('₹2,767.00')).toBeInTheDocument()
    expect(screen.getByText('₹33,204.00 a year · 4 active')).toBeInTheDocument()
    expect(screen.getByText('₹768.00 to pay')).toBeInTheDocument()
    expect(screen.getByText('Needs attention').closest('button')).toHaveTextContent('3') // 2 overdue + 1 to confirm
    expect(screen.getByText(/2 overdue \(₹3,499.00\)/)).toBeInTheDocument()
    expect(screen.getByText('1 paid · 1 missed')).toBeInTheDocument()
  })

  it('clicking "Needs attention" filters the table to overdue', async () => {
    const onPick = vi.fn()
    render(<SummaryTiles summary={summary} onPick={onPick} />)

    await userEvent.click(screen.getByText('Needs attention').closest('button'))

    expect(onPick).toHaveBeenCalledWith({ paymentStatus: 'OVERDUE', dueWithinDays: '', view: '', month: '' })
  })

  it('clicking "Paid this month" opens the paid bills tab', async () => {
    const onPick = vi.fn()
    render(<SummaryTiles summary={summary} onPick={onPick} />)

    await userEvent.click(screen.getByText('Paid this month').closest('button'))

    expect(onPick).toHaveBeenCalledWith({ view: 'paid', month: '', paymentStatus: '', dueWithinDays: '' })
  })

  it('says all caught up when nothing needs attention', () => {
    render(<SummaryTiles summary={{ ...summary, overdueCount: 0, awaitingConfirmationCount: 0 }} />)
    expect(screen.getByText('All caught up')).toBeInTheDocument()
  })
})

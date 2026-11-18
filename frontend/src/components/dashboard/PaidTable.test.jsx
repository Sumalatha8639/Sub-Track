import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { longMonth, shiftMonth } from '../../utils/dates'
import PaidTable from './PaidTable.jsx'

vi.mock('../../context/AuthContext.jsx', () => ({ useAuth: () => ({ user: { timezone: 'Asia/Kolkata' } }) }))

const data = {
  month: '2026-11',
  total: 948,
  count: 2,
  items: [
    {
      subscriptionId: 1,
      name: 'Netflix',
      category: 'OTT',
      billingCycle: 'MONTHLY',
      subscriptionStatus: 'ACTIVE',
      period: { id: 11, dueDate: '2026-11-20', amountDue: 649, amountPaid: 649, paidOn: '2026-11-16', paymentStatus: 'PAID', note: 'UPI' },
    },
    {
      subscriptionId: 2,
      name: 'Amazon Prime',
      category: 'OTT',
      billingCycle: 'MONTHLY',
      subscriptionStatus: 'ACTIVE',
      period: { id: 12, dueDate: '2026-11-14', amountDue: 299, amountPaid: 299, paidOn: '2026-11-14', paymentStatus: 'PAID', note: null },
    },
  ],
}

function renderTable(props = {}) {
  const onMonth = vi.fn()
  render(
    <MemoryRouter>
      <PaidTable data={data} currentMonth="2026-11" onMonth={onMonth} {...props} />
    </MemoryRouter>,
  )
  return { onMonth }
}

describe('PaidTable', () => {
  it('lists the bills paid in the month with the total', () => {
    renderTable()
    expect(screen.getByText('November 2026')).toBeInTheDocument()
    expect(screen.getByText('₹948.00')).toBeInTheDocument()
    expect(screen.getByText(/2 bills paid/)).toBeInTheDocument()
    expect(screen.getByText('Netflix')).toBeInTheDocument()
    expect(screen.getByText('16 Nov 2026')).toBeInTheDocument()
    expect(screen.getByText('UPI')).toBeInTheDocument()
  })

  it('pages to the previous month but not past the current one', async () => {
    const { onMonth } = renderTable()
    expect(screen.getByLabelText('Next month')).toBeDisabled()

    await userEvent.click(screen.getByLabelText('Previous month'))

    expect(onMonth).toHaveBeenCalledWith('2026-10')
  })

  it('shows an empty state for a month with no payments', () => {
    renderTable({ data: { month: '2026-09', total: 0, count: 0, items: [] } })
    expect(screen.getByText('Nothing paid in September 2026')).toBeInTheDocument()
    expect(screen.getByText('This month')).toBeInTheDocument()
  })

  it('month helpers handle year boundaries', () => {
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
    expect(longMonth('2026-11')).toBe('November 2026')
  })
})

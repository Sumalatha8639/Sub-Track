import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MarkPaidDialog from './MarkPaidDialog.jsx'

// A plain function rather than vi.fn(): Vitest's own tracking of a vi.fn's rejected
// promise was reported as a test failure even though the component handles it.
const api = vi.hoisted(() => ({ calls: [], impl: async () => ({}) }))
vi.mock('../../api/subscriptions', () => ({
  markPeriod: (...args) => {
    api.calls.push(args)
    return api.impl(...args)
  },
}))

const period = { id: 7, dueDate: '2026-11-20', amountDue: 649, paymentStatus: 'PENDING' }

function renderDialog() {
  const onDone = vi.fn()
  const onClose = vi.fn()
  render(
    <MarkPaidDialog
      subscriptionId={3}
      subscriptionName="Netflix"
      period={period}
      today="2026-11-16"
      onDone={onDone}
      onClose={onClose}
    />,
  )
  return { onDone, onClose }
}

describe('MarkPaidDialog', () => {
  beforeEach(() => {
    api.calls = []
    api.impl = async () => ({})
  })

  it('defaults to today and the amount due', () => {
    renderDialog()
    expect(screen.getByLabelText('Paid on')).toHaveValue('2026-11-16')
    expect(screen.getByLabelText('Amount paid (₹)')).toHaveValue('649')
  })

  it('rejects a paid-on date in the future', async () => {
    const user = userEvent.setup()
    renderDialog()
    const date = screen.getByLabelText('Paid on')
    await user.clear(date)
    await user.type(date, '2026-11-17')
    await user.click(screen.getByRole('button', { name: 'Mark paid' }))

    expect(screen.getByText("Paid-on date can't be in the future")).toBeInTheDocument()
    expect(api.calls).toHaveLength(0)
  })

  it('rejects zero and over-precise amounts', async () => {
    const user = userEvent.setup()
    renderDialog()
    const amount = screen.getByLabelText('Amount paid (₹)')

    await user.clear(amount)
    await user.type(amount, '0')
    await user.click(screen.getByRole('button', { name: 'Mark paid' }))
    expect(screen.getByText('Enter an amount greater than 0')).toBeInTheDocument()

    await user.clear(amount)
    await user.type(amount, '10.123')
    await user.click(screen.getByRole('button', { name: 'Mark paid' }))
    expect(screen.getByText('Use at most 2 decimal places')).toBeInTheDocument()
    expect(api.calls).toHaveLength(0)
  })

  it('submits a valid payment and reports the result', async () => {
    const user = userEvent.setup()
    const result = { period: { id: 7, paymentStatus: 'PAID' }, subscription: { id: 3 } }
    api.impl = async () => result
    const { onDone } = renderDialog()

    await user.type(screen.getByLabelText('Note (optional)'), 'UPI')
    await user.click(screen.getByRole('button', { name: 'Mark paid' }))

    expect(api.calls[0]).toEqual([3, 7, { status: 'PAID', paidOn: '2026-11-16', amountPaid: 649, note: 'UPI' }])
    await waitFor(() => expect(onDone).toHaveBeenCalledWith(result))
  })

  it('shows the server message when saving fails', async () => {
    const user = userEvent.setup()
    api.impl = async () => {
      // Shaped like an AxiosError: an Error with a response attached
      throw Object.assign(new Error('Request failed with status code 400'), {
        response: { status: 400, data: { message: 'This period was already marked' } },
      })
    }
    const { onDone } = renderDialog()

    await user.click(screen.getByRole('button', { name: 'Mark paid' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('This period was already marked'))
    expect(onDone).not.toHaveBeenCalled()
  })
})

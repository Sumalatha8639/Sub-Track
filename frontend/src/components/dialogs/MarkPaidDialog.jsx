import { useState } from 'react'
import { markPeriod } from '../../api/subscriptions'
import { apiError } from '../../utils/errors'
import { formatDate, formatInr } from '../../utils/format'
import Alert from '../ui/Alert.jsx'
import Button from '../ui/Button.jsx'
import Field from '../ui/Field.jsx'
import Modal from '../ui/Modal.jsx'

/**
 * Mark a billing period as PAID (or edit an already-paid one).
 * `today` is YYYY-MM-DD in the user's timezone; paid-on can't be after it.
 */
export default function MarkPaidDialog({ subscriptionId, subscriptionName, period, today, onClose, onDone }) {
  const [form, setForm] = useState({
    paidOn: period.paidOn ?? today,
    amountPaid: String(period.amountPaid ?? period.amountDue),
    note: period.note ?? '',
  })
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  const validate = () => {
    const next = {}
    if (!form.paidOn) next.paidOn = 'Choose the date you paid'
    else if (form.paidOn > today) next.paidOn = "Paid-on date can't be in the future"
    const amount = Number(form.amountPaid)
    if (!form.amountPaid || Number.isNaN(amount) || amount <= 0) next.amountPaid = 'Enter an amount greater than 0'
    else if (!/^\d+(\.\d{1,2})?$/.test(form.amountPaid.trim())) next.amountPaid = 'Use at most 2 decimal places'
    return next
  }

  const onSubmit = async (e) => {
    e.preventDefault()
    const local = validate()
    setErrors(local)
    setMessage('')
    if (Object.keys(local).length) return
    setSaving(true)
    try {
      const result = await markPeriod(subscriptionId, period.id, {
        status: 'PAID',
        paidOn: form.paidOn,
        amountPaid: Number(form.amountPaid),
        note: form.note.trim() || null,
      })
      onDone(result)
    } catch (err) {
      const { message: msg, fieldErrors } = apiError(err)
      setErrors(fieldErrors)
      setMessage(msg)
      setSaving(false)
    }
  }

  return (
    <Modal
      title={`Mark ${subscriptionName} as paid`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="success" type="submit" form="mark-paid-form" loading={saving}>
            Mark paid
          </Button>
        </>
      }
    >
      <p className="mb-4 text-sm text-slate-600">
        Due {formatDate(period.dueDate)} · {formatInr(period.amountDue)}
      </p>
      <form id="mark-paid-form" onSubmit={onSubmit} className="space-y-4" noValidate>
        <Alert>{message}</Alert>
        <Field label="Paid on" type="date" max={today} value={form.paidOn} onChange={set('paidOn')} error={errors.paidOn} />
        <Field
          label="Amount paid (₹)"
          inputMode="decimal"
          value={form.amountPaid}
          onChange={set('amountPaid')}
          error={errors.amountPaid}
        />
        <Field
          as="textarea"
          rows={2}
          label="Note (optional)"
          maxLength={500}
          value={form.note}
          onChange={set('note')}
          error={errors.note}
        />
      </form>
    </Modal>
  )
}

import { useState } from 'react'
import { markPeriod } from '../../api/subscriptions'
import { apiError } from '../../utils/errors'
import { formatDate, formatInr } from '../../utils/format'
import Alert from '../ui/Alert.jsx'
import Button from '../ui/Button.jsx'
import Field from '../ui/Field.jsx'
import Modal from '../ui/Modal.jsx'

/** Mark a billing period as MISSED. Only allowed on or after its due date. */
export default function MarkMissedDialog({ subscriptionId, subscriptionName, period, onClose, onDone }) {
  const [note, setNote] = useState(period.note ?? '')
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)

  const onSubmit = async (e) => {
    e.preventDefault()
    setMessage('')
    setSaving(true)
    try {
      onDone(await markPeriod(subscriptionId, period.id, { status: 'MISSED', note: note.trim() || null }))
    } catch (err) {
      setMessage(apiError(err).message)
      setSaving(false)
    }
  }

  return (
    <Modal
      title={`Mark ${subscriptionName} as missed`}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="danger" type="submit" form="mark-missed-form" loading={saving}>
            Mark missed
          </Button>
        </>
      }
    >
      <p className="mb-4 text-sm text-slate-600">
        Due {formatDate(period.dueDate)} · {formatInr(period.amountDue)}. The subscription moves on to its next due
        date. If you've stopped using it, cancel the subscription instead.
      </p>
      <form id="mark-missed-form" onSubmit={onSubmit} className="space-y-4" noValidate>
        <Alert>{message}</Alert>
        <Field
          as="textarea"
          rows={2}
          label="Note (optional)"
          maxLength={500}
          placeholder="e.g. Policy lapsed, will renew next year"
          value={note}
          onChange={(e) => setNote(e.target.value)}
        />
      </form>
    </Modal>
  )
}

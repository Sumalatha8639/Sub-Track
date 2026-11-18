import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { createSubscription, getSubscription, updateSubscription } from '../api/subscriptions'
import Alert from '../components/ui/Alert.jsx'
import Button from '../components/ui/Button.jsx'
import Field from '../components/ui/Field.jsx'
import { ErrorBlock, LoadingBlock, PageHeader } from '../components/ui/States.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { addDays, todayIn } from '../utils/dates'
import { apiError } from '../utils/errors'
import { formatDate } from '../utils/format'
import { CATEGORY_LABELS, CYCLE_LABELS, DEFAULT_REMINDERS } from '../utils/labels'
import { parseReminderDays } from '../utils/reminders'

const EMPTY = {
  name: '',
  category: 'OTT',
  amount: '',
  billingCycle: 'MONTHLY',
  nextDueDate: '',
  autoRenew: false,
  notes: '',
  reminderDays: DEFAULT_REMINDERS.MONTHLY.join(', '),
}

/** Create (/subscriptions/new) or edit (/subscriptions/:id/edit) a subscription. */
export default function SubscriptionForm() {
  const { id } = useParams()
  const editing = Boolean(id)
  const navigate = useNavigate()
  const { user } = useAuth()
  const toast = useToast()
  const today = todayIn(user?.timezone ?? 'Asia/Kolkata')
  const [form, setForm] = useState(EMPTY)
  const [remindersTouched, setRemindersTouched] = useState(false)
  const [loadState, setLoadState] = useState(editing ? 'loading' : 'ready')
  const [loadError, setLoadError] = useState('')
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!editing) return
    getSubscription(id)
      .then((s) => {
        setForm({
          name: s.name,
          category: s.category,
          amount: String(s.amount),
          billingCycle: s.billingCycle,
          nextDueDate: s.nextDueDate,
          autoRenew: s.autoRenew,
          notes: s.notes ?? '',
          reminderDays: s.reminderDays.join(', '),
        })
        setLoadState('ready')
      })
      .catch((err) => {
        setLoadError(apiError(err).message)
        setLoadState('error')
      })
  }, [editing, id])

  const set = (key) => (e) => {
    const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value
    setForm((f) => {
      const next = { ...f, [key]: value }
      if (key === 'billingCycle') {
        if (value === 'ONE_TIME') next.autoRenew = false
        // Follow the cycle's default reminders until the user edits them
        if (!editing && !remindersTouched) next.reminderDays = DEFAULT_REMINDERS[value].join(', ')
      }
      return next
    })
    if (key === 'reminderDays') setRemindersTouched(true)
  }

  const validate = () => {
    const next = {}
    if (!form.name.trim()) next.name = 'Enter a name'
    const amount = Number(form.amount)
    if (!form.amount || Number.isNaN(amount) || amount <= 0) next.amount = 'Enter an amount greater than 0'
    else if (!/^\d+(\.\d{1,2})?$/.test(form.amount.trim())) next.amount = 'Use at most 2 decimal places'
    if (!form.nextDueDate) next.nextDueDate = 'Choose the next due date'
    else if (form.nextDueDate < addDays(today, -366)) next.nextDueDate = 'That is more than a year ago'
    if (!editing) {
      const parsed = parseReminderDays(form.reminderDays)
      if (parsed.error) next.reminderDays = parsed.error
    }
    return next
  }

  const onSubmit = async (e) => {
    e.preventDefault()
    const local = validate()
    setErrors(local)
    setMessage('')
    if (Object.keys(local).length) return
    const body = {
      name: form.name.trim(),
      category: form.category,
      amount: Number(form.amount),
      billingCycle: form.billingCycle,
      nextDueDate: form.nextDueDate,
      autoRenew: form.autoRenew,
      notes: form.notes.trim() || null,
      ...(editing ? {} : { reminderDays: parseReminderDays(form.reminderDays).days }),
    }
    setSaving(true)
    try {
      const saved = editing ? await updateSubscription(id, body) : await createSubscription(body)
      toast.show(editing ? `${saved.name} updated` : `${saved.name} added`, {
        description: `Next due ${formatDate(saved.nextDueDate)}`,
      })
      navigate(`/subscriptions/${saved.id}`, { replace: true })
    } catch (err) {
      const { message: msg, fieldErrors } = apiError(err)
      setErrors(fieldErrors)
      setMessage(msg)
      setSaving(false)
    }
  }

  if (loadState === 'loading') return <LoadingBlock />
  if (loadState === 'error') return <ErrorBlock message={loadError} />

  return (
    <section className="max-w-3xl space-y-6">
      <PageHeader
        title={editing ? 'Edit subscription' : 'Add subscription'}
        subtitle={
          editing
            ? 'Changing the due date or billing cycle restarts the schedule from the new date.'
            : 'Enter the next date this bill is due. Later due dates are worked out from it.'
        }
      />
      <form
        onSubmit={onSubmit}
        className="space-y-5 rounded-xl border border-slate-200/80 bg-white p-6 shadow-[0_1px_2px_rgba(15,23,42,0.04)]"
        noValidate
      >
        <Alert>{message}</Alert>
        <Field label="Name" placeholder="e.g. Netflix, Star Health, Jio" value={form.name} onChange={set('name')} error={errors.name} maxLength={100} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field as="select" label="Category" value={form.category} onChange={set('category')} error={errors.category}>
            {Object.entries(CATEGORY_LABELS).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </Field>
          <Field label="Amount (₹)" inputMode="decimal" placeholder="649" value={form.amount} onChange={set('amount')} error={errors.amount} />
          <Field as="select" label="Billing cycle" value={form.billingCycle} onChange={set('billingCycle')} error={errors.billingCycle}>
            {Object.entries(CYCLE_LABELS).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </Field>
          <Field label="Next due date" type="date" value={form.nextDueDate} onChange={set('nextDueDate')} error={errors.nextDueDate} />
        </div>
        <label className="flex items-start gap-3 text-sm">
          <input
            type="checkbox"
            className="mt-0.5 h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-600"
            checked={form.autoRenew}
            disabled={form.billingCycle === 'ONE_TIME'}
            onChange={set('autoRenew')}
          />
          <span>
            <span className="font-medium text-slate-800">Renews automatically</span>
            <span className="block text-slate-500">
              The due date moves forward by itself; you just confirm whether it was paid.
            </span>
          </span>
        </label>
        {!editing && (
          <Field
            label="Remind me (days before)"
            value={form.reminderDays}
            onChange={set('reminderDays')}
            error={errors.reminderDays}
            hint="Comma-separated, 1–90. Leave empty for no reminders."
          />
        )}
        <Field as="textarea" rows={3} label="Notes (optional)" maxLength={500} value={form.notes} onChange={set('notes')} error={errors.notes} />
        <div className="flex justify-end gap-2">
          <Link
            to={editing ? `/subscriptions/${id}` : '/subscriptions'}
            className="rounded-lg px-3.5 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-100"
          >
            Cancel
          </Link>
          <Button type="submit" loading={saving}>
            {editing ? 'Save changes' : 'Add subscription'}
          </Button>
        </div>
      </form>
    </section>
  )
}

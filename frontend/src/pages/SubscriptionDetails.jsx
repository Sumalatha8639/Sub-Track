import { Ban, BellRing, ChevronRight, Ellipsis, Pause, Pencil, Play, RefreshCw, Trash } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import {
  changeStatus,
  deleteSubscription,
  getSubscription,
  listPeriods,
  periodHistory,
  updateReminders,
} from '../api/subscriptions'
import CategoryIcon from '../components/CategoryIcon.jsx'
import ConfirmDialog from '../components/dialogs/ConfirmDialog.jsx'
import PeriodActions from '../components/PeriodActions.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import Alert from '../components/ui/Alert.jsx'
import Button from '../components/ui/Button.jsx'
import { Card, CardHeader } from '../components/ui/Card.jsx'
import Field from '../components/ui/Field.jsx'
import Menu from '../components/ui/Menu.jsx'
import { ErrorBlock, LoadingBlock } from '../components/ui/States.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { apiError } from '../utils/errors'
import { notifyDataChanged } from '../utils/events'
import { formatDate, formatInr } from '../utils/format'
import { CATEGORY_LABELS, CYCLE_LABELS, PAYMENT_STATUS, daysLeftText } from '../utils/labels'
import { parseReminderDays } from '../utils/reminders'

export default function SubscriptionDetails() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [sub, setSub] = useState(null)
  const [periods, setPeriods] = useState([])
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [busy, setBusy] = useState('')
  const [confirm, setConfirm] = useState(null) // 'delete' | 'cancel' | null

  const load = useCallback(
    () =>
      Promise.all([getSubscription(id), listPeriods(id)])
        .then(([s, p]) => {
          setSub(s)
          setPeriods(p)
          setError('')
        })
        .catch((err) => setError(apiError(err).message)),
    [id],
  )

  useEffect(() => {
    load()
  }, [load])

  const runAction = async (name, fn, message) => {
    setBusy(name)
    setActionError('')
    try {
      await fn()
      notifyDataChanged()
      if (message) toast.show(message)
    } catch (err) {
      setActionError(apiError(err).message)
    } finally {
      setBusy('')
      setConfirm(null)
    }
  }

  const setStatus = (status, message) => runAction(status, async () => setSub(await changeStatus(id, status)), message)
  const remove = () =>
    runAction(
      'delete',
      async () => {
        await deleteSubscription(id)
        navigate('/subscriptions', { replace: true })
      },
      `${sub.name} deleted`,
    )

  if (error) return <ErrorBlock message={error} onRetry={load} />
  if (!sub) return <LoadingBlock />

  const current = periods.find((p) => p.current)
  const unconfirmed = periods.filter((p) => !p.current && p.paymentStatus === 'PENDING')
  const paidTotal = periods.filter((p) => p.paymentStatus === 'PAID').reduce((sum, p) => sum + Number(p.amountPaid), 0)
  const canEdit = sub.status !== 'EXPIRED'

  const menuItems = [
    ...(sub.status === 'ACTIVE' ? [{ label: 'Pause reminders', icon: Pause, onClick: () => setStatus('PAUSED', `${sub.name} paused`) }] : []),
    ...(sub.status === 'PAUSED' ? [{ label: 'Resume', icon: Play, onClick: () => setStatus('ACTIVE', `${sub.name} resumed`) }] : []),
    ...(sub.status === 'CANCELLED' ? [{ label: 'Reactivate', icon: RefreshCw, onClick: () => setStatus('ACTIVE', `${sub.name} reactivated`) }] : []),
    ...(sub.status === 'ACTIVE' || sub.status === 'PAUSED'
      ? [{ label: 'Cancel subscription', icon: Ban, onClick: () => setConfirm('cancel') }]
      : []),
    'divider',
    { label: 'Delete', icon: Trash, onClick: () => setConfirm('delete'), danger: true },
  ]

  return (
    <section className="space-y-6">
      <nav className="flex items-center gap-1.5 text-sm text-slate-500" aria-label="Breadcrumb">
        <Link to="/subscriptions" className="hover:text-slate-800">
          Subscriptions
        </Link>
        <ChevronRight size={14} aria-hidden="true" />
        <span className="truncate text-slate-800">{sub.name}</span>
      </nav>

      <Card className="p-5">
        <div className="flex flex-wrap items-center gap-4">
          <CategoryIcon category={sub.category} size="lg" />
          <div className="mr-auto min-w-0">
            <h2 className="flex flex-wrap items-center gap-2 text-xl font-semibold tracking-tight text-slate-900">
              {sub.name} <StatusBadge kind="subscription" status={sub.status} />
            </h2>
            <p className="mt-0.5 text-sm text-slate-500">
              {CATEGORY_LABELS[sub.category]} · {CYCLE_LABELS[sub.billingCycle]} · {sub.autoRenew ? 'Renews automatically' : 'Manual renewal'}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {canEdit && (
              <Link
                to={`/subscriptions/${id}/edit`}
                className="inline-flex items-center gap-1.5 rounded-lg bg-white px-3.5 py-2 text-sm font-semibold text-slate-700 shadow-sm ring-1 ring-slate-200 ring-inset hover:bg-slate-50"
              >
                <Pencil size={15} /> Edit
              </Link>
            )}
            <Menu
              items={menuItems}
              trigger={({ toggle, open }) => (
                <button
                  type="button"
                  onClick={toggle}
                  aria-expanded={open}
                  aria-label="More actions"
                  disabled={Boolean(busy)}
                  className="rounded-lg p-2 text-slate-600 shadow-sm ring-1 ring-slate-200 ring-inset hover:bg-slate-50"
                >
                  <Ellipsis size={18} />
                </button>
              )}
            />
          </div>
        </div>
      </Card>

      <Alert>{actionError}</Alert>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Amount" value={formatInr(sub.amount)} note={CYCLE_LABELS[sub.billingCycle]} />
        <Stat label="Per month" value={sub.billingCycle === 'ONE_TIME' ? '—' : formatInr(sub.monthlyAmount)} note="Normalised monthly cost" />
        <Stat
          label="Next due"
          value={formatDate(sub.nextDueDate)}
          note={sub.status === 'ACTIVE' ? daysLeftText(sub.daysLeft) : 'Not active'}
          warn={sub.status === 'ACTIVE' && sub.daysLeft < 0}
        />
        <Stat label="Paid so far" value={formatInr(paidTotal)} note={`${periods.filter((p) => p.paymentStatus === 'PAID').length} payments recorded`} />
      </div>

      {unconfirmed.length > 0 && (
        <Alert tone="warning">
          {unconfirmed.length === 1 ? 'One earlier cycle has' : `${unconfirmed.length} earlier cycles have`} not been marked
          yet. Mark them in the payment history so your records stay accurate.
        </Alert>
      )}

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <CardHeader title="This cycle" subtitle="The payment due next" />
          <div className="p-5">
            {current ? (
              <>
                <div className="flex items-center justify-between gap-3">
                  <p className="tabular text-2xl font-semibold text-slate-900">{formatInr(current.amountDue)}</p>
                  <StatusBadge status={current.displayStatus} />
                </div>
                <p className="mt-1 text-sm text-slate-500">
                  Due {formatDate(current.dueDate)} · {daysLeftText(current.daysLeft)}
                </p>
                {sub.status !== 'CANCELLED' && (
                  <div className="mt-5">
                    <PeriodActions subscriptionId={sub.id} subscriptionName={sub.name} period={current} size="md" onChanged={load} />
                  </div>
                )}
              </>
            ) : (
              <p className="text-sm text-slate-500">No open billing cycle.</p>
            )}
          </div>
        </Card>

        <RemindersCard sub={sub} onSaved={setSub} />
      </div>

      <Card>
        <CardHeader title="Payment history" subtitle="Every billing cycle, newest first" />
        {periods.length === 0 ? (
          <p className="px-5 py-6 text-sm text-slate-500">No billing cycles yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60 text-xs tracking-wide text-slate-500 uppercase">
                  <th className="py-3 pr-3 pl-5 font-medium">Due date</th>
                  <th className="px-3 py-3 text-right font-medium">Amount due</th>
                  <th className="px-3 py-3 font-medium">Status</th>
                  <th className="px-3 py-3 font-medium">Payment</th>
                  <th className="px-3 py-3 font-medium">Note</th>
                  <th className="py-3 pr-5 pl-3" aria-hidden="true" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {periods.map((p) => (
                  <PeriodRow key={p.id} sub={sub} period={p} onChanged={load} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {confirm === 'delete' && (
        <ConfirmDialog title={`Delete ${sub.name}?`} confirmLabel="Delete" busy={busy === 'delete'} onConfirm={remove} onClose={() => setConfirm(null)}>
          It disappears from your lists and reminders stop. Payment history is kept on the server.
        </ConfirmDialog>
      )}
      {confirm === 'cancel' && (
        <ConfirmDialog
          title={`Cancel ${sub.name}?`}
          confirmLabel="Cancel subscription"
          busy={busy === 'CANCELLED'}
          onConfirm={() => setStatus('CANCELLED', `${sub.name} cancelled`)}
          onClose={() => setConfirm(null)}
        >
          Reminders stop. You can reactivate it later.
        </ConfirmDialog>
      )}
    </section>
  )
}

function Stat({ label, value, note, warn }) {
  return (
    <Card className="p-4">
      <p className="text-xs font-medium text-slate-500">{label}</p>
      <p className="tabular mt-1.5 text-lg font-semibold text-slate-900">{value}</p>
      <p className={`mt-0.5 text-xs ${warn ? 'font-medium text-rose-600' : 'text-slate-500'}`}>{note}</p>
    </Card>
  )
}

function RemindersCard({ sub, onSaved }) {
  const toast = useToast()
  const [text, setText] = useState(sub.reminderDays.join(', '))
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const dirty = text.trim() !== sub.reminderDays.join(', ')

  const save = async (e) => {
    e.preventDefault()
    const parsed = parseReminderDays(text)
    if (parsed.error) {
      setError(parsed.error)
      return
    }
    setError('')
    setSaving(true)
    try {
      const updated = await updateReminders(sub.id, parsed.days)
      onSaved(updated)
      setText(updated.reminderDays.join(', '))
      toast.show('Reminders updated', {
        description: updated.reminderDays.length
          ? `${updated.reminderDays.join(', ')} days before each due date`
          : 'Reminders are off for this subscription',
      })
    } catch (err) {
      setError(apiError(err).message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card className="lg:col-span-2">
      <CardHeader title="Email reminders" subtitle="Sent around 9 AM your time, this many days before each due date" />
      <form onSubmit={save} className="p-5" noValidate>
        <div className="mb-4 flex flex-wrap gap-2">
          {sub.reminderDays.length ? (
            sub.reminderDays.map((d) => (
              <span key={d} className="inline-flex items-center gap-1.5 rounded-full bg-indigo-50 px-3 py-1 text-xs font-medium text-indigo-700">
                <BellRing size={12} aria-hidden="true" /> {d === 1 ? '1 day before' : `${d} days before`}
              </span>
            ))
          ) : (
            <span className="text-sm text-slate-500">Reminders are off.</span>
          )}
        </div>
        <div className="flex flex-wrap items-start gap-2">
          <Field
            aria-label="Reminder days"
            className="w-full max-w-xs"
            value={text}
            onChange={(e) => setText(e.target.value)}
            error={error}
            hint="Comma-separated days, 1–90. Empty turns reminders off."
          />
          <Button type="submit" variant="secondary" loading={saving} disabled={!dirty}>
            Save
          </Button>
        </div>
      </form>
    </Card>
  )
}

function PeriodRow({ sub, period, onChanged }) {
  const [history, setHistory] = useState(null)
  const [open, setOpen] = useState(false)

  const toggleHistory = async () => {
    setOpen((o) => !o)
    if (!history) {
      try {
        setHistory(await periodHistory(period.id))
      } catch {
        setHistory([])
      }
    }
  }

  return (
    <>
      <tr className="hover:bg-slate-50/70">
        <td className="py-3 pr-3 pl-5">
          <span className="font-medium text-slate-900">{formatDate(period.dueDate)}</span>
          {period.current && <span className="ml-2 rounded bg-indigo-50 px-1.5 py-0.5 text-[10px] font-semibold text-indigo-700 uppercase">Current</span>}
        </td>
        <td className="tabular px-3 py-3 text-right text-slate-900">{formatInr(period.amountDue)}</td>
        <td className="px-3 py-3">
          <StatusBadge status={period.displayStatus} />
        </td>
        <td className="px-3 py-3 text-xs text-slate-600">
          {period.paymentStatus === 'PAID' ? (
            <>
              <span className="text-slate-800">{formatInr(period.amountPaid)}</span>
              <span className="block">on {formatDate(period.paidOn)}</span>
            </>
          ) : (
            '—'
          )}
        </td>
        <td className="max-w-48 truncate px-3 py-3 text-xs text-slate-500" title={period.note ?? ''}>
          {period.note ?? (period.markedAt ? '' : '—')}
          {period.markedAt && (
            <button type="button" onClick={toggleHistory} className={`font-medium text-indigo-600 hover:underline ${period.note ? 'ml-2' : ''}`}>
              {open ? 'Hide log' : 'Change log'}
            </button>
          )}
        </td>
        <td className="py-3 pr-5 pl-3">
          {sub.status !== 'CANCELLED' && (
            <PeriodActions layout="compact" subscriptionId={sub.id} subscriptionName={sub.name} period={period} onChanged={onChanged} />
          )}
        </td>
      </tr>
      {open && (
        <tr className="bg-slate-50/60">
          <td colSpan={6} className="px-5 py-2 text-xs text-slate-500">
            {history === null
              ? 'Loading…'
              : history.length === 0
                ? 'No changes recorded.'
                : history.map((h) => (
                    <p key={h.id}>
                      {PAYMENT_STATUS[h.oldStatus]?.label ?? 'Pending'} → {PAYMENT_STATUS[h.newStatus]?.label} on{' '}
                      {new Date(h.changedAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}
                    </p>
                  ))}
          </td>
        </tr>
      )}
    </>
  )
}

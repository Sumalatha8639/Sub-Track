import { CircleCheck, CircleX, Ellipsis, Eye, Pencil } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../context/AuthContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { todayIn } from '../utils/dates'
import { notifyDataChanged } from '../utils/events'
import { formatDate } from '../utils/format'
import MarkMissedDialog from './dialogs/MarkMissedDialog.jsx'
import MarkPaidDialog from './dialogs/MarkPaidDialog.jsx'
import Button from './ui/Button.jsx'
import Menu from './ui/Menu.jsx'

/**
 * Mark Paid / Mark Missed for one billing period, with their dialogs and a confirmation toast.
 * - layout="buttons": both actions as buttons (details page)
 * - layout="compact": primary action + "…" menu (tables)
 */
export default function PeriodActions({
  subscriptionId,
  subscriptionName,
  period,
  onChanged,
  size = 'sm',
  layout = 'buttons',
}) {
  const { user } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [open, setOpen] = useState(null) // 'paid' | 'missed' | null
  const today = todayIn(user?.timezone ?? 'Asia/Kolkata')
  const canMiss = period.dueDate <= today
  const status = period.paymentStatus

  const done = (kind) => (result) => {
    setOpen(null)
    toast.show(`${subscriptionName} marked as ${kind}`, {
      description:
        result?.subscription && result.subscription.nextDueDate !== period.dueDate
          ? `Next due ${formatDate(result.subscription.nextDueDate)}`
          : `Cycle due ${formatDate(period.dueDate)}`,
    })
    notifyDataChanged()
    onChanged?.(result)
  }

  const paidLabel = status === 'PAID' ? 'Edit payment' : status === 'MISSED' ? 'Change to paid' : 'Mark paid'
  const missedLabel = status === 'PAID' ? 'Change to missed' : 'Mark missed'
  const missTitle = canMiss ? undefined : 'Can be marked missed from its due date'

  return (
    <>
      {layout === 'compact' ? (
        <div className="flex items-center justify-end gap-1">
          {status === 'PENDING' ? (
            <Button size={size} variant="success" onClick={() => setOpen('paid')}>
              <CircleCheck size={14} /> Mark paid
            </Button>
          ) : null}
          <Menu
            trigger={({ toggle, open: isOpen }) => (
              <button
                type="button"
                onClick={toggle}
                aria-expanded={isOpen}
                aria-label={`More actions for ${subscriptionName}`}
                className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
              >
                <Ellipsis size={18} />
              </button>
            )}
            items={[
              ...(status !== 'PENDING' ? [{ label: paidLabel, icon: Pencil, onClick: () => setOpen('paid') }] : []),
              ...(status !== 'MISSED'
                ? [{ label: missedLabel, icon: CircleX, onClick: () => setOpen('missed'), disabled: !canMiss, title: missTitle }]
                : []),
              'divider',
              { label: 'View details', icon: Eye, onClick: () => navigate(`/subscriptions/${subscriptionId}`) },
            ]}
          />
        </div>
      ) : (
        <div className="flex flex-wrap gap-2">
          {status !== 'PAID' && (
            <Button size={size} variant="success" onClick={() => setOpen('paid')}>
              <CircleCheck size={14} /> {paidLabel}
            </Button>
          )}
          {status === 'PAID' && (
            <Button size={size} variant="secondary" onClick={() => setOpen('paid')}>
              <Pencil size={14} /> Edit payment
            </Button>
          )}
          {status !== 'MISSED' && (
            <Button size={size} variant="secondary" disabled={!canMiss} title={missTitle} onClick={() => setOpen('missed')}>
              <CircleX size={14} /> {missedLabel}
            </Button>
          )}
        </div>
      )}
      {open === 'paid' && (
        <MarkPaidDialog
          subscriptionId={subscriptionId}
          subscriptionName={subscriptionName}
          period={period}
          today={today}
          onClose={() => setOpen(null)}
          onDone={done('paid')}
        />
      )}
      {open === 'missed' && (
        <MarkMissedDialog
          subscriptionId={subscriptionId}
          subscriptionName={subscriptionName}
          period={period}
          onClose={() => setOpen(null)}
          onDone={done('missed')}
        />
      )}
    </>
  )
}

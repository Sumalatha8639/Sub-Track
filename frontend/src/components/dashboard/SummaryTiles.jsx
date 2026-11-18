import { CalendarClock, CircleCheck, TriangleExclamationPoint, Wallet } from 'lucide-react'
import { formatInr } from '../../utils/format'
import { Card } from '../ui/Card.jsx'

function Kpi({ icon: Icon, tone, label, value, children, onClick, filterLabel }) {
  const body = (
    <>
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-medium text-slate-500">{label}</p>
        <span className={`inline-flex h-9 w-9 items-center justify-center rounded-lg ${tone}`}>
          <Icon size={18} aria-hidden="true" />
        </span>
      </div>
      <p className="tabular mt-2 text-[28px] leading-none font-semibold tracking-tight text-slate-900">{value}</p>
      <div className="mt-3 text-xs text-slate-500">{children}</div>
    </>
  )
  return onClick ? (
    <Card className="transition hover:border-indigo-200 hover:shadow-md">
      <button
        type="button"
        onClick={onClick}
        className="block h-full w-full rounded-xl p-5 text-left focus-visible:outline-2 focus-visible:outline-indigo-600"
        title={filterLabel}
      >
        {body}
      </button>
    </Card>
  ) : (
    <Card className="p-5">{body}</Card>
  )
}

/** KPI cards (project plan §3.3). Clicking a card filters the table below. */
export default function SummaryTiles({ summary, onPick }) {
  const attention = summary.overdueCount + summary.awaitingConfirmationCount
  const pick = (filter) => (onPick ? () => onPick(filter) : undefined)

  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Kpi icon={Wallet} tone="bg-indigo-50 text-indigo-600" label="Monthly commitment" value={formatInr(summary.monthlyCommitment)}>
        {formatInr(summary.yearlyCommitment)} a year · {summary.activeCount} active
      </Kpi>

      <Kpi
        icon={CalendarClock}
        tone="bg-sky-50 text-sky-600"
        label="Due in next 30 days"
        value={summary.dueIn30DaysCount}
        onClick={pick({ dueWithinDays: '30', paymentStatus: '', view: '', month: '' })}
        filterLabel="Show what's due in the next 30 days"
      >
        {formatInr(summary.dueIn30DaysAmount)} to pay
      </Kpi>

      <Kpi
        icon={TriangleExclamationPoint}
        tone={attention ? 'bg-rose-50 text-rose-600' : 'bg-slate-100 text-slate-500'}
        label="Needs attention"
        value={attention}
        onClick={pick({
          paymentStatus: summary.overdueCount ? 'OVERDUE' : 'AWAITING_CONFIRMATION',
          dueWithinDays: '',
          view: '',
          month: '',
        })}
        filterLabel="Show what needs attention"
      >
        {attention ? (
          <span className="flex flex-wrap gap-x-3 gap-y-1">
            <span className="inline-flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-rose-500" aria-hidden="true" />
              {summary.overdueCount} overdue ({formatInr(summary.overdueAmount)})
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="h-1.5 w-1.5 rounded-full bg-orange-400" aria-hidden="true" />
              {summary.awaitingConfirmationCount} to confirm
            </span>
          </span>
        ) : (
          'All caught up'
        )}
      </Kpi>

      <Kpi
        icon={CircleCheck}
        tone="bg-emerald-50 text-emerald-600"
        label="Paid this month"
        value={formatInr(summary.paidThisMonthAmount)}
        onClick={pick({ view: 'paid', month: '', paymentStatus: '', dueWithinDays: '' })}
        filterLabel="Show the bills paid this month"
      >
        {summary.paidThisMonthCount} paid · {summary.missedThisMonthCount} missed
      </Kpi>
    </div>
  )
}

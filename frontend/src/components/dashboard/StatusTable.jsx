import { CircleCheck } from 'lucide-react'
import { Link } from 'react-router'
import { formatDate, formatInr } from '../../utils/format'
import { CATEGORY_LABELS, CYCLE_LABELS, daysLeftText } from '../../utils/labels'
import CategoryIcon from '../CategoryIcon.jsx'
import PeriodActions from '../PeriodActions.jsx'
import StatusBadge from '../StatusBadge.jsx'
import { Card, CardHeader } from '../ui/Card.jsx'
import { EmptyState, LoadingBlock } from '../ui/States.jsx'
import PaidTable from './PaidTable.jsx'

/** Status tabs filter the board; the "Paid" tab switches to the paid-bills view. */
const TABS = [
  { value: '', label: 'All' },
  { value: 'OVERDUE', label: 'Overdue' },
  { value: 'AWAITING_CONFIRMATION', label: 'To confirm' },
  { value: 'DUE_SOON', label: 'Due soon' },
  { value: 'UPCOMING', label: 'Upcoming' },
  { value: 'paid', label: 'Paid', view: 'paid' },
]

const SELECT =
  'rounded-lg border-0 bg-white py-1.5 pr-8 pl-3 text-sm text-slate-700 shadow-sm ring-1 ring-slate-200 ring-inset focus:ring-2 focus:ring-indigo-500'

/** Every subscription with its payment status and quick actions (project plan §3.3). */
export default function StatusTable({ rows, paid, currentMonth, filters, onFilter, onChanged }) {
  const paidView = filters.view === 'paid'
  const filtered = Object.values(filters).some(Boolean)

  return (
    <Card>
      <CardHeader
        title="Payment status"
        subtitle={paidView ? 'Bills you paid, by the date you paid them' : 'Every subscription and the cycle that needs you next'}
        actions={
          !paidView && (
          <div className="flex flex-wrap items-center gap-2">
            <select aria-label="Category" className={SELECT} value={filters.category} onChange={(e) => onFilter({ category: e.target.value })}>
              <option value="">All categories</option>
              {Object.entries(CATEGORY_LABELS).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
            <select aria-label="Due within" className={SELECT} value={filters.dueWithinDays} onChange={(e) => onFilter({ dueWithinDays: e.target.value })}>
              <option value="">Any due date</option>
              <option value="7">Due in 7 days</option>
              <option value="30">Due in 30 days</option>
            </select>
            <select aria-label="Sort by" className={SELECT} value={filters.sort} onChange={(e) => onFilter({ sort: e.target.value })}>
              <option value="">Sort: needs action</option>
              <option value="dueDate">Sort: due date</option>
              <option value="amount">Sort: amount</option>
              <option value="name">Sort: name</option>
            </select>
          </div>
          )
        }
      />

      <div className="flex gap-1 overflow-x-auto overflow-y-hidden border-b border-slate-100 px-5" role="tablist" aria-label="Payment status">
        {TABS.map((t) => {
          const active = t.view ? paidView : !paidView && (filters.paymentStatus ?? '') === t.value
          return (
            <button
              key={t.label}
              type="button"
              role="tab"
              aria-selected={active}
              onClick={() =>
                onFilter(t.view ? { view: 'paid', paymentStatus: '' } : { view: '', month: '', paymentStatus: t.value })
              }
              className={`-mb-px inline-flex items-center gap-1.5 border-b-2 px-3 py-2.5 text-sm font-medium whitespace-nowrap transition-colors ${
                active ? 'border-indigo-600 text-indigo-700' : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}
            >
              {t.view && <CircleCheck size={14} className={active ? 'text-emerald-600' : 'text-slate-400'} aria-hidden="true" />}
              {t.label}
            </button>
          )
        })}
      </div>

      {paidView ? (
        paid ? (
          <PaidTable data={paid} currentMonth={currentMonth} onMonth={(m) => onFilter({ month: m })} onChanged={onChanged} />
        ) : (
          <LoadingBlock />
        )
      ) : rows.length === 0 ? (
        <div className="p-5">
          {filtered ? (
            <EmptyState title="Nothing matches these filters" />
          ) : (
            <EmptyState
              title="No subscriptions yet"
              action={
                <Link to="/subscriptions/new" className="text-sm font-semibold text-indigo-600 hover:underline">
                  Add your first subscription →
                </Link>
              }
            />
          )}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[860px] text-left text-sm">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50/60 text-xs font-medium tracking-wide text-slate-500 uppercase">
                <th className="py-3 pr-3 pl-5 font-medium">Subscription</th>
                <th className="px-3 py-3 text-right font-medium">Amount</th>
                <th className="px-3 py-3 font-medium">Due</th>
                <th className="px-3 py-3 font-medium">Status</th>
                <th className="px-3 py-3 font-medium">Last cycle</th>
                <th className="py-3 pr-5 pl-3 text-right font-medium">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map((r) => (
                <tr key={r.subscriptionId} className="transition-colors hover:bg-slate-50/70">
                  <td className="py-3 pr-3 pl-5">
                    <div className="flex items-center gap-3">
                      <CategoryIcon category={r.category} size="sm" />
                      <div className="min-w-0">
                        <Link to={`/subscriptions/${r.subscriptionId}`} className="font-medium text-slate-900 hover:text-indigo-600">
                          {r.name}
                        </Link>
                        <p className="flex items-center gap-1.5 text-xs text-slate-500">
                          {CATEGORY_LABELS[r.category]}
                          {r.status !== 'ACTIVE' && <StatusBadge kind="subscription" status={r.status} />}
                        </p>
                      </div>
                    </div>
                  </td>
                  <td className="tabular px-3 py-3 text-right">
                    <span className="font-medium text-slate-900">{formatInr(r.amount)}</span>
                    <span className="block text-xs text-slate-500">{CYCLE_LABELS[r.billingCycle]}</span>
                  </td>
                  <td className="px-3 py-3">
                    {r.attention ? (
                      <>
                        <span className="text-slate-900">{formatDate(r.attention.dueDate)}</span>
                        <span className={`block text-xs ${r.attention.daysLeft < 0 ? 'font-medium text-rose-600' : 'text-slate-500'}`}>
                          {daysLeftText(r.attention.daysLeft)}
                        </span>
                      </>
                    ) : (
                      <span className="text-slate-400">—</span>
                    )}
                  </td>
                  <td className="px-3 py-3">
                    {r.attention ? <StatusBadge status={r.attention.displayStatus} /> : <span className="text-slate-400">—</span>}
                    {r.unmarkedPastCount > 1 && (
                      <span className="mt-1 block text-xs text-amber-700">{r.unmarkedPastCount} cycles unmarked</span>
                    )}
                  </td>
                  <td className="px-3 py-3 text-xs text-slate-500">
                    {r.lastClosed ? (
                      r.lastClosed.paymentStatus === 'PAID' ? (
                        <>
                          <span className="text-slate-700">Paid {formatInr(r.lastClosed.amountPaid)}</span>
                          <span className="block">{formatDate(r.lastClosed.paidOn)}</span>
                        </>
                      ) : (
                        <>
                          <span className="text-slate-700">Missed</span>
                          <span className="block">due {formatDate(r.lastClosed.dueDate)}</span>
                        </>
                      )
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="py-3 pr-5 pl-3">
                    {r.attention ? (
                      <PeriodActions
                        layout="compact"
                        subscriptionId={r.subscriptionId}
                        subscriptionName={r.name}
                        period={r.attention}
                        onChanged={onChanged}
                      />
                    ) : (
                      <div className="text-right">
                        <Link to={`/subscriptions/${r.subscriptionId}`} className="text-xs font-medium text-indigo-600 hover:underline">
                          View
                        </Link>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}

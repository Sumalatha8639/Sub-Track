import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Link } from 'react-router'
import { longMonth, shiftMonth } from '../../utils/dates'
import { formatDate, formatInr } from '../../utils/format'
import { CATEGORY_LABELS, CYCLE_LABELS } from '../../utils/labels'
import CategoryIcon from '../CategoryIcon.jsx'
import PeriodActions from '../PeriodActions.jsx'
import StatusBadge from '../StatusBadge.jsx'
import { EmptyState } from '../ui/States.jsx'

/**
 * "Paid" tab: every bill paid in the selected month, with the month's total.
 * `currentMonth` is this month ("2026-11"); you can't page past it.
 */
export default function PaidTable({ data, currentMonth, onMonth, onChanged }) {
  const atCurrent = data.month >= currentMonth

  return (
    <>
      <div className="flex flex-wrap items-center gap-3 border-b border-slate-100 px-5 py-3">
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => onMonth(shiftMonth(data.month, -1))}
            className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800"
            aria-label="Previous month"
          >
            <ChevronLeft size={18} />
          </button>
          <span className="min-w-36 text-center text-sm font-semibold text-slate-900">{longMonth(data.month)}</span>
          <button
            type="button"
            onClick={() => onMonth(shiftMonth(data.month, 1))}
            disabled={atCurrent}
            className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 disabled:cursor-not-allowed disabled:opacity-30"
            aria-label="Next month"
          >
            <ChevronRight size={18} />
          </button>
          {!atCurrent && (
            <button
              type="button"
              onClick={() => onMonth('')}
              className="ml-1 rounded-md px-2 py-1 text-xs font-medium text-indigo-600 hover:bg-indigo-50"
            >
              This month
            </button>
          )}
        </div>
        <p className="ml-auto text-sm text-slate-500">
          {data.count} {data.count === 1 ? 'bill' : 'bills'} paid ·{' '}
          <span className="tabular font-semibold text-slate-900">{formatInr(data.total)}</span>
        </p>
      </div>

      {data.items.length === 0 ? (
        <div className="p-5">
          <EmptyState title={`Nothing paid in ${longMonth(data.month)}`}>
            Bills you mark as paid appear here, grouped by the date you paid them.
          </EmptyState>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[820px] text-left text-sm">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50/60 text-xs tracking-wide text-slate-500 uppercase">
                <th className="py-3 pr-3 pl-5 font-medium">Subscription</th>
                <th className="px-3 py-3 font-medium">Paid on</th>
                <th className="px-3 py-3 text-right font-medium">Amount paid</th>
                <th className="px-3 py-3 font-medium">For due date</th>
                <th className="px-3 py-3 font-medium">Note</th>
                <th className="py-3 pr-5 pl-3">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.items.map((item) => {
                const p = item.period
                const differs = Number(p.amountPaid) !== Number(p.amountDue)
                return (
                  <tr key={p.id} className="transition-colors hover:bg-slate-50/70">
                    <td className="py-3 pr-3 pl-5">
                      <div className="flex items-center gap-3">
                        <CategoryIcon category={item.category} size="sm" />
                        <div className="min-w-0">
                          <Link to={`/subscriptions/${item.subscriptionId}`} className="font-medium text-slate-900 hover:text-indigo-600">
                            {item.name}
                          </Link>
                          <p className="flex items-center gap-1.5 text-xs text-slate-500">
                            {CATEGORY_LABELS[item.category]} · {CYCLE_LABELS[item.billingCycle]}
                            {item.subscriptionStatus !== 'ACTIVE' && <StatusBadge kind="subscription" status={item.subscriptionStatus} />}
                          </p>
                        </div>
                      </div>
                    </td>
                    <td className="px-3 py-3">
                      <span className="inline-flex items-center gap-2">
                        <StatusBadge status="PAID" />
                        <span className="text-slate-900">{formatDate(p.paidOn)}</span>
                      </span>
                    </td>
                    <td className="tabular px-3 py-3 text-right">
                      <span className="font-medium text-slate-900">{formatInr(p.amountPaid)}</span>
                      {differs && <span className="block text-xs text-slate-500">of {formatInr(p.amountDue)} due</span>}
                    </td>
                    <td className="px-3 py-3 text-slate-600">{formatDate(p.dueDate)}</td>
                    <td className="max-w-48 truncate px-3 py-3 text-xs text-slate-500" title={p.note ?? ''}>
                      {p.note ?? '—'}
                    </td>
                    <td className="py-3 pr-5 pl-3">
                      <PeriodActions
                        layout="compact"
                        subscriptionId={item.subscriptionId}
                        subscriptionName={item.name}
                        period={p}
                        onChanged={onChanged}
                      />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

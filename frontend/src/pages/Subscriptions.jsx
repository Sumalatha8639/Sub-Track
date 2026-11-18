import { ChevronRight, Plus, Search } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { listSubscriptions } from '../api/subscriptions'
import CategoryIcon from '../components/CategoryIcon.jsx'
import StatusBadge from '../components/StatusBadge.jsx'
import { Card } from '../components/ui/Card.jsx'
import { EmptyState, ErrorBlock, LoadingBlock, PageHeader, Pagination } from '../components/ui/States.jsx'
import { apiError } from '../utils/errors'
import { formatDate, formatInr } from '../utils/format'
import { CATEGORY_LABELS, CYCLE_LABELS, SUBSCRIPTION_STATUS, daysLeftText } from '../utils/labels'

const FILTER_KEYS = ['q', 'category', 'status', 'dueWithinDays', 'sort', 'direction']

const SELECT =
  'rounded-lg border-0 bg-white py-2 pr-8 pl-3 text-sm text-slate-700 shadow-sm ring-1 ring-slate-200 ring-inset focus:ring-2 focus:ring-indigo-500'

const COLUMNS = [
  { key: 'name', label: 'Subscription' },
  { key: null, label: 'Category' },
  { key: 'amount', label: 'Amount', align: 'right' },
  { key: null, label: 'Cycle' },
  { key: 'nextDueDate', label: 'Next due' },
  { key: null, label: 'Status' },
]

export default function Subscriptions() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const filters = Object.fromEntries(FILTER_KEYS.map((k) => [k, params.get(k) ?? '']))
  const page = Number(params.get('page') ?? 0)
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [search, setSearch] = useState(filters.q)

  const query = params.toString()
  const load = useCallback(() => {
    const current = new URLSearchParams(query)
    listSubscriptions({
      ...Object.fromEntries(FILTER_KEYS.map((k) => [k, current.get(k) ?? ''])),
      page: current.get('page') ?? 0,
      size: 15,
    })
      .then((res) => {
        setData(res)
        setError('')
      })
      .catch((err) => setError(apiError(err).message))
  }, [query])

  useEffect(load, [load])

  // Keep the box in sync when the top-bar search changes ?q=
  const urlQ = params.get('q') ?? ''
  const [lastUrlQ, setLastUrlQ] = useState(urlQ)
  if (urlQ !== lastUrlQ) {
    setLastUrlQ(urlQ)
    setSearch(urlQ)
  }

  // Debounce the search box into the URL
  useEffect(() => {
    const t = setTimeout(() => {
      if (search !== (params.get('q') ?? '')) update({ q: search })
    }, 300)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search])

  function update(changes) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v === '' || v === null ? next.delete(k) : next.set(k, v)))
    if (!('page' in changes)) next.delete('page')
    setParams(next, { replace: true })
  }

  const sortBy = (key) => {
    const current = filters.sort || 'nextDueDate'
    const dir = current === key && filters.direction !== 'desc' ? 'desc' : 'asc'
    update({ sort: key, direction: dir })
  }

  const hasFilters = FILTER_KEYS.some((k) => filters[k])
  const activeSort = filters.sort || 'nextDueDate'

  return (
    <section className="space-y-6">
      <PageHeader
        title="Subscriptions"
        subtitle="Everything you pay for, sorted by what's due next."
        actions={
          <Link
            to="/subscriptions/new"
            className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500"
          >
            <Plus size={16} /> Add subscription
          </Link>
        }
      />

      <Card>
        <div className="flex flex-wrap items-center gap-2 border-b border-slate-100 p-4">
          <label className="relative min-w-56 flex-1">
            <span className="sr-only">Search by name</span>
            <Search size={16} className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-slate-400" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by name…"
              className="w-full rounded-lg border-0 py-2 pr-3 pl-9 text-sm shadow-sm ring-1 ring-slate-200 ring-inset placeholder:text-slate-400 focus:ring-2 focus:ring-indigo-500"
            />
          </label>
          <select aria-label="Category" className={SELECT} value={filters.category} onChange={(e) => update({ category: e.target.value })}>
            <option value="">All categories</option>
            {Object.entries(CATEGORY_LABELS).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
          <select aria-label="Status" className={SELECT} value={filters.status} onChange={(e) => update({ status: e.target.value })}>
            <option value="">Any status</option>
            {Object.entries(SUBSCRIPTION_STATUS).map(([k, v]) => (
              <option key={k} value={k}>
                {v.label}
              </option>
            ))}
          </select>
          <select aria-label="Due within" className={SELECT} value={filters.dueWithinDays} onChange={(e) => update({ dueWithinDays: e.target.value })}>
            <option value="">Any due date</option>
            <option value="7">Due in 7 days</option>
            <option value="30">Due in 30 days</option>
            <option value="90">Due in 90 days</option>
          </select>
          {hasFilters && (
            <button
              type="button"
              onClick={() => {
                setSearch('')
                setParams(new URLSearchParams(), { replace: true })
              }}
              className="rounded-lg px-3 py-2 text-sm font-medium text-slate-500 hover:bg-slate-100 hover:text-slate-700"
            >
              Clear
            </button>
          )}
        </div>

        {error ? (
          <div className="p-5">
            <ErrorBlock message={error} onRetry={load} />
          </div>
        ) : !data ? (
          <LoadingBlock />
        ) : data.content.length === 0 ? (
          <div className="p-5">
            {hasFilters ? (
              <EmptyState title="No subscriptions match these filters" />
            ) : (
              <EmptyState
                title="No subscriptions yet"
                action={
                  <Link to="/subscriptions/new" className="text-sm font-semibold text-indigo-600 hover:underline">
                    Add your first subscription →
                  </Link>
                }
              >
                Add insurance, recharges, OTT, AI tools and anything else you renew.
              </EmptyState>
            )}
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-100 bg-slate-50/60 text-xs tracking-wide text-slate-500 uppercase">
                    {COLUMNS.map((c, i) => (
                      <th
                        key={c.label}
                        className={`py-3 font-medium ${i === 0 ? 'pr-3 pl-5' : 'px-3'} ${c.align === 'right' ? 'text-right' : ''}`}
                        aria-sort={c.key && activeSort === c.key ? (filters.direction === 'desc' ? 'descending' : 'ascending') : undefined}
                      >
                        {c.key ? (
                          <button type="button" onClick={() => sortBy(c.key)} className="inline-flex items-center gap-1 uppercase hover:text-slate-800">
                            {c.label}
                            {activeSort === c.key && <span aria-hidden="true">{filters.direction === 'desc' ? '↓' : '↑'}</span>}
                          </button>
                        ) : (
                          c.label
                        )}
                      </th>
                    ))}
                    <th className="py-3 pr-5" aria-hidden="true" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.content.map((s) => (
                    <tr
                      key={s.id}
                      onClick={() => navigate(`/subscriptions/${s.id}`)}
                      className="cursor-pointer transition-colors hover:bg-slate-50/70"
                    >
                      <td className="py-3 pr-3 pl-5">
                        <div className="flex items-center gap-3">
                          <CategoryIcon category={s.category} size="sm" />
                          <Link
                            to={`/subscriptions/${s.id}`}
                            onClick={(e) => e.stopPropagation()}
                            className="font-medium text-slate-900 hover:text-indigo-600"
                          >
                            {s.name}
                          </Link>
                        </div>
                      </td>
                      <td className="px-3 py-3 text-slate-600">{CATEGORY_LABELS[s.category]}</td>
                      <td className="tabular px-3 py-3 text-right font-medium text-slate-900">{formatInr(s.amount)}</td>
                      <td className="px-3 py-3 text-slate-600">
                        {CYCLE_LABELS[s.billingCycle]}
                        {s.autoRenew && <span className="ml-1.5 text-xs text-slate-400">· auto</span>}
                      </td>
                      <td className="px-3 py-3">
                        <span className="text-slate-900">{formatDate(s.nextDueDate)}</span>
                        {s.status === 'ACTIVE' && (
                          <span className={`block text-xs ${s.daysLeft < 0 ? 'font-medium text-rose-600' : 'text-slate-500'}`}>
                            {daysLeftText(s.daysLeft)}
                          </span>
                        )}
                      </td>
                      <td className="px-3 py-3">
                        <StatusBadge kind="subscription" status={s.status} />
                      </td>
                      <td className="py-3 pr-5 text-right text-slate-300">
                        <ChevronRight size={18} className="inline" aria-hidden="true" />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              onPage={(p) => update({ page: String(p) })}
            />
          </>
        )}
      </Card>
    </section>
  )
}

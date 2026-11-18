import { Suspense, lazy, useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { getCategoryBreakdown, getPaid, getStatusBoard, getSummary, getTrend } from '../api/dashboard'
import StatusTable from '../components/dashboard/StatusTable.jsx'
import SummaryTiles from '../components/dashboard/SummaryTiles.jsx'
import { ErrorBlock, LoadingBlock, PageHeader } from '../components/ui/States.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { todayIn } from '../utils/dates'
import { apiError } from '../utils/errors'

// Recharts is large; load it separately so login and other pages stay light
const DashboardCharts = lazy(() => import('../components/dashboard/Charts.jsx'))

// view=paid shows the "Paid" tab; month picks its month (empty = this month)
const FILTER_KEYS = ['paymentStatus', 'category', 'dueWithinDays', 'sort', 'view', 'month']

/** Status dashboard: tiles, every subscription's payment status, and charts (project plan §3.3). */
export default function Dashboard() {
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const filters = Object.fromEntries(FILTER_KEYS.map((k) => [k, params.get(k) ?? '']))
  const filterKey = FILTER_KEYS.map((k) => params.get(k) ?? '').join('|')

  const [summary, setSummary] = useState(null)
  const [rows, setRows] = useState(null)
  const [paid, setPaid] = useState(null)
  const [charts, setCharts] = useState(null)
  const currentMonth = todayIn(user.timezone).slice(0, 7)
  const [error, setError] = useState('')
  // Computed once per visit: greeting and today's date for the header
  const [{ greeting, today }] = useState(() => {
    const now = new Date()
    const hour = now.getHours()
    return {
      greeting: hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening',
      today: now.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }),
    }
  })

  const loadBoard = useCallback(() => {
    const [paymentStatus, category, dueWithinDays, sort, view, month] = filterKey.split('|')
    const board = getStatusBoard({ paymentStatus, category, dueWithinDays, sort }).then(setRows)
    const paidBills = view === 'paid' ? getPaid(month).then(setPaid) : Promise.resolve()
    return Promise.all([board, paidBills])
  }, [filterKey])

  const loadSummaryAndCharts = useCallback(
    () =>
      Promise.all([getSummary(), getCategoryBreakdown(), getTrend(12)]).then(([s, categories, trend]) => {
        setSummary(s)
        setCharts({ categories, trend })
      }),
    [],
  )

  const loadAll = useCallback(
    () =>
      Promise.all([loadBoard(), loadSummaryAndCharts()])
        .then(() => setError(''))
        .catch((err) => setError(apiError(err).message)),
    [loadBoard, loadSummaryAndCharts],
  )

  useEffect(() => {
    loadAll()
  }, [loadAll])

  const setFilters = (changes) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)))
    setParams(next, { replace: true })
  }

  if (error) return <ErrorBlock message={error} onRetry={loadAll} />
  if (!summary || !rows || !charts) return <LoadingBlock label="Loading your dashboard…" />

  return (
    <section className="space-y-6">
      <PageHeader
        title={`${greeting}, ${user.name.split(' ')[0]}`}
        subtitle={`${today} · Here's where all your subscriptions stand.`}
        actions={
          <Link
            to="/subscriptions"
            className="inline-flex items-center gap-1.5 rounded-lg bg-white px-3.5 py-2 text-sm font-semibold text-slate-700 shadow-sm ring-1 ring-slate-200 ring-inset hover:bg-slate-50"
          >
            View all subscriptions
          </Link>
        }
      />

      <SummaryTiles summary={summary} onPick={setFilters} />

      <StatusTable
        rows={rows}
        paid={filters.view === 'paid' ? paid : null}
        currentMonth={currentMonth}
        filters={filters}
        onFilter={setFilters}
        onChanged={loadAll}
      />

      <Suspense fallback={<LoadingBlock label="Loading charts…" />}>
        <DashboardCharts categories={charts.categories} trend={charts.trend} />
      </Suspense>
    </section>
  )
}

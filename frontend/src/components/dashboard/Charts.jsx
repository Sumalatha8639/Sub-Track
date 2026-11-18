import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatInr, monthLabel } from '../../utils/format'
import { CATEGORY_LABELS } from '../../utils/labels'

/**
 * Dashboard charts. Colour roles (dataviz reference palette):
 * - one-series magnitude charts use series slot 1 (blue)
 * - paid vs missed are states, so they use the fixed status colours (good / critical),
 *   always with a legend, tooltip labels and a table view, never colour alone.
 */
const C = {
  series: '#2a78d6',
  paid: '#0ca30c',
  missed: '#d03b3b',
  grid: '#e1e0d9',
  axis: '#898781',
  surface: '#ffffff',
}

const AXIS = { stroke: C.axis, fontSize: 12, tickLine: false, axisLine: false }

/** ₹1.2L / ₹15k style ticks so the axis stays narrow. */
function compactInr(v) {
  if (v >= 100000) return `₹${(v / 100000).toFixed(v >= 1000000 ? 0 : 1)}L`
  if (v >= 1000) return `₹${(v / 1000).toFixed(v >= 10000 ? 0 : 1)}k`
  return `₹${v}`
}

function ChartTooltip({ active, payload, label, money = true, labelFormat = (l) => l }) {
  if (!active || !payload?.length) return null
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow-md">
      <p className="mb-1 font-medium text-slate-900">{labelFormat(label)}</p>
      {payload.map((p) => (
        <p key={p.dataKey} className="flex items-center gap-2 text-slate-600">
          <span className="h-2 w-2 rounded-sm" style={{ background: p.color }} aria-hidden="true" />
          {p.name}: <span className="font-semibold text-slate-900">{money ? formatInr(p.value) : p.value}</span>
        </p>
      ))}
    </div>
  )
}

function ChartCard({ title, subtitle, children, table }) {
  return (
    <figure className="rounded-xl border border-slate-200/80 bg-white shadow-[0_1px_2px_rgba(15,23,42,0.04)]">
      <figcaption className="border-b border-slate-100 px-5 py-4">
        <h3 className="text-[15px] font-semibold text-slate-900">{title}</h3>
        {subtitle && <p className="mt-0.5 text-xs text-slate-500">{subtitle}</p>}
      </figcaption>
      <div className="h-64 px-3 pt-4">{children}</div>
      <details className="border-t border-slate-100 px-5 py-2.5 text-xs text-slate-600">
        <summary className="cursor-pointer font-medium text-indigo-600">View as table</summary>
        <div className="mt-2 max-h-48 overflow-auto">{table}</div>
      </details>
    </figure>
  )
}

function SimpleTable({ head, rows }) {
  return (
    <table className="w-full text-left tabular-nums">
      <thead>
        <tr className="text-slate-500">
          {head.map((h) => (
            <th key={h} className="py-1 pr-3 font-medium">
              {h}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r[0]} className="border-t border-slate-100">
            {r.map((c, i) => (
              <td key={i} className="py-1 pr-3">
                {c}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export function CategoryChart({ data }) {
  const rows = data.map((d) => ({ ...d, label: CATEGORY_LABELS[d.category], monthlyAmount: Number(d.monthlyAmount) }))
  return (
    <ChartCard
      title="Monthly spend by category"
      subtitle="Active subscriptions, per month"
      table={
        <SimpleTable
          head={['Category', 'Subscriptions', 'Per month']}
          rows={rows.map((r) => [r.label, r.count, formatInr(r.monthlyAmount)])}
        />
      }
    >
      {rows.length === 0 ? (
        <Empty />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 16, bottom: 0, left: 0 }}>
            <CartesianGrid horizontal={false} stroke={C.grid} />
            <XAxis type="number" {...AXIS} tickFormatter={compactInr} />
            <YAxis type="category" dataKey="label" {...AXIS} width={110} />
            <Tooltip cursor={{ fill: '#f1f5f9' }} content={<ChartTooltip />} />
            <Bar dataKey="monthlyAmount" name="Per month" fill={C.series} radius={[0, 4, 4, 0]} maxBarSize={22} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  )
}

export function PaidTrendChart({ data }) {
  const rows = data.map((d) => ({ ...d, paidAmount: Number(d.paidAmount) }))
  const empty = rows.every((r) => r.paidAmount === 0)
  return (
    <ChartCard
      title="Paid per month"
      subtitle="What you actually paid, by paid-on date"
      table={
        <SimpleTable
          head={['Month', 'Paid', 'Payments']}
          rows={rows.map((r) => [monthLabel(r.month), formatInr(r.paidAmount), r.paidCount])}
        />
      }
    >
      {empty ? (
        <Empty text="Mark payments as paid to see your spending here." />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke={C.grid} />
            <XAxis dataKey="month" {...AXIS} tickFormatter={monthLabel} interval="preserveStartEnd" />
            <YAxis {...AXIS} tickFormatter={compactInr} width={48} />
            <Tooltip cursor={{ fill: '#f1f5f9' }} content={<ChartTooltip labelFormat={monthLabel} />} />
            <Bar dataKey="paidAmount" name="Paid" fill={C.series} radius={[4, 4, 0, 0]} maxBarSize={28} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  )
}

export function PaidVsMissedChart({ data }) {
  const empty = data.every((r) => r.paidCount === 0 && r.missedCount === 0)
  return (
    <ChartCard
      title="Paid vs missed"
      subtitle="Billing cycles per month"
      table={
        <SimpleTable
          head={['Month', 'Paid', 'Missed']}
          rows={data.map((r) => [monthLabel(r.month), r.paidCount, r.missedCount])}
        />
      }
    >
      {empty ? (
        <Empty text="Nothing marked yet." />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke={C.grid} />
            <XAxis dataKey="month" {...AXIS} tickFormatter={monthLabel} interval="preserveStartEnd" />
            <YAxis {...AXIS} allowDecimals={false} width={32} />
            <Tooltip cursor={{ fill: '#f1f5f9' }} content={<ChartTooltip money={false} labelFormat={monthLabel} />} />
            <Legend iconType="square" iconSize={10} wrapperStyle={{ fontSize: 12, color: '#52514e' }} />
            {/* 2px surface-coloured stroke separates the stacked segments */}
            <Bar dataKey="paidCount" name="Paid" stackId="s" fill={C.paid} stroke={C.surface} strokeWidth={2} maxBarSize={28} />
            <Bar
              dataKey="missedCount"
              name="Missed"
              stackId="s"
              fill={C.missed}
              stroke={C.surface}
              strokeWidth={2}
              radius={[4, 4, 0, 0]}
              maxBarSize={28}
            />
          </BarChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  )
}

/** The three dashboard charts. Default export so the page can lazy-load Recharts. */
export default function DashboardCharts({ categories, trend }) {
  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <CategoryChart data={categories} />
      <PaidTrendChart data={trend} />
      <PaidVsMissedChart data={trend} />
    </div>
  )
}

function Empty({ text = 'No active subscriptions yet.' }) {
  return <div className="flex h-full items-center justify-center text-sm text-slate-400">{text}</div>
}

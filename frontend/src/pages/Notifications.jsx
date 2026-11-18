import { BellRing, Mail } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { listNotifications } from '../api/subscriptions'
import StatusBadge from '../components/StatusBadge.jsx'
import { Card } from '../components/ui/Card.jsx'
import { EmptyState, ErrorBlock, LoadingBlock, PageHeader, Pagination } from '../components/ui/States.jsx'
import { apiError } from '../utils/errors'
import { formatDate } from '../utils/format'

function describe(n) {
  if (n.type === 'OVERDUE') return 'Overdue nudge: did you pay?'
  return n.daysBefore === 1 ? 'Reminder: due tomorrow' : `Reminder: ${n.daysBefore} days before`
}

const fmtTime = (iso) => new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })

export default function Notifications() {
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    listNotifications(page)
      .then((res) => {
        setData(res)
        setError('')
      })
      .catch((err) => setError(apiError(err).message))
  }, [page])

  useEffect(load, [load])

  return (
    <section className="space-y-6">
      <PageHeader title="Notifications" subtitle="Every reminder SubTrack has emailed you, newest first." />
      <Card>
        {error ? (
          <div className="p-5">
            <ErrorBlock message={error} onRetry={load} />
          </div>
        ) : !data ? (
          <LoadingBlock />
        ) : data.content.length === 0 ? (
          <div className="p-5">
            <EmptyState title="No notifications yet">
              Reminders appear here once they're sent. Make sure your email is verified.
            </EmptyState>
          </div>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-100 bg-slate-50/60 text-xs tracking-wide text-slate-500 uppercase">
                    <th className="py-3 pr-3 pl-5 font-medium">Notification</th>
                    <th className="px-3 py-3 font-medium">Subscription</th>
                    <th className="px-3 py-3 font-medium">Due date</th>
                    <th className="px-3 py-3 font-medium">Sent</th>
                    <th className="py-3 pr-5 pl-3 font-medium">Delivery</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.content.map((n) => (
                    <tr key={n.id} className="hover:bg-slate-50/70">
                      <td className="py-3 pr-3 pl-5">
                        <span className="flex items-center gap-3">
                          <span
                            className={`inline-flex h-8 w-8 items-center justify-center rounded-lg ${
                              n.type === 'OVERDUE' ? 'bg-rose-50 text-rose-600' : 'bg-indigo-50 text-indigo-600'
                            }`}
                          >
                            {n.type === 'OVERDUE' ? <BellRing size={16} /> : <Mail size={16} />}
                          </span>
                          <span className="text-slate-700">{describe(n)}</span>
                        </span>
                      </td>
                      <td className="px-3 py-3">
                        <Link to={`/subscriptions/${n.subscriptionId}`} className="font-medium text-slate-900 hover:text-indigo-600">
                          {n.subscriptionName}
                        </Link>
                      </td>
                      <td className="px-3 py-3 text-slate-600">{formatDate(n.dueDate)}</td>
                      <td className="px-3 py-3 text-slate-600">{fmtTime(n.sentAt ?? n.createdAt)}</td>
                      <td className="py-3 pr-5 pl-3">
                        <StatusBadge kind="notification" status={n.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
          </>
        )}
      </Card>
    </section>
  )
}

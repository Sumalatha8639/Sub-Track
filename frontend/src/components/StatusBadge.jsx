import { NOTIFICATION_STATUS, PAYMENT_STATUS, SUBSCRIPTION_STATUS } from '../utils/labels'
import Badge from './ui/Badge.jsx'

const KINDS = {
  payment: PAYMENT_STATUS,
  subscription: SUBSCRIPTION_STATUS,
  notification: NOTIFICATION_STATUS,
}

/**
 * One badge for every status in the app, so colours match everywhere.
 * kind: 'payment' (display status of a billing period), 'subscription' or 'notification'.
 */
export default function StatusBadge({ status, kind = 'payment' }) {
  const meta = KINDS[kind]?.[status]
  if (!meta) return <Badge className="bg-slate-100 text-slate-600 ring-slate-500/20">{status ?? '—'}</Badge>
  return <Badge className={meta.className}>{meta.label}</Badge>
}

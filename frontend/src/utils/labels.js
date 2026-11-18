export const CATEGORY_LABELS = {
  INSURANCE: 'Insurance',
  MOBILE_RECHARGE: 'Mobile recharge',
  OTT: 'OTT / Streaming',
  AI_TOOL: 'AI tool',
  INTERNET: 'Internet',
  UTILITY: 'Utility',
  OTHER: 'Other',
}

export const CYCLE_LABELS = {
  MONTHLY: 'Monthly',
  QUARTERLY: 'Quarterly',
  HALF_YEARLY: 'Half-yearly',
  YEARLY: 'Yearly',
  ONE_TIME: 'One-time',
}

/** Default reminder days per cycle; mirrors BillingCycle.defaultReminderDays on the backend. */
export const DEFAULT_REMINDERS = {
  MONTHLY: [7, 3, 1],
  QUARTERLY: [15, 7, 3, 1],
  HALF_YEARLY: [30, 15, 7, 3, 1],
  YEARLY: [30, 15, 7, 3, 1],
  ONE_TIME: [30, 15, 7, 3, 1],
}

/** Payment display statuses (project plan §3.3). Colours are defined once here. */
export const PAYMENT_STATUS = {
  PAID: { label: 'Paid', className: 'bg-emerald-100 text-emerald-800 ring-emerald-600/20' },
  UPCOMING: { label: 'Upcoming', className: 'bg-sky-100 text-sky-800 ring-sky-600/20' },
  DUE_SOON: { label: 'Due soon', className: 'bg-yellow-100 text-yellow-800 ring-yellow-600/20' },
  DUE_TODAY: { label: 'Due today', className: 'bg-amber-100 text-amber-900 ring-amber-600/30' },
  AWAITING_CONFIRMATION: {
    label: 'Awaiting confirmation',
    className: 'bg-orange-100 text-orange-800 ring-orange-600/20',
  },
  OVERDUE: { label: 'Overdue', className: 'bg-rose-100 text-rose-800 ring-rose-600/20' },
  MISSED: { label: 'Missed', className: 'bg-slate-800 text-white ring-slate-900/20' },
}

export const SUBSCRIPTION_STATUS = {
  ACTIVE: { label: 'Active', className: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20' },
  PAUSED: { label: 'Paused', className: 'bg-slate-100 text-slate-700 ring-slate-500/20' },
  CANCELLED: { label: 'Cancelled', className: 'bg-slate-100 text-slate-500 ring-slate-500/20' },
  EXPIRED: { label: 'Expired', className: 'bg-slate-100 text-slate-500 ring-slate-500/20' },
}

export const NOTIFICATION_STATUS = {
  SENT: { label: 'Sent', className: 'bg-emerald-100 text-emerald-800 ring-emerald-600/20' },
  PENDING: { label: 'Sending', className: 'bg-sky-100 text-sky-800 ring-sky-600/20' },
  FAILED: { label: 'Failed, will retry', className: 'bg-amber-100 text-amber-900 ring-amber-600/30' },
  ABANDONED: { label: 'Not sent', className: 'bg-slate-100 text-slate-600 ring-slate-500/20' },
}

/** 5 -> "5 days left", 1 -> "Due tomorrow", 0 -> "Due today", -3 -> "Overdue by 3 days". */
export function daysLeftText(days) {
  if (days === null || days === undefined) return ''
  if (days > 1) return `${days} days left`
  if (days === 1) return 'Due tomorrow'
  if (days === 0) return 'Due today'
  if (days === -1) return 'Overdue by 1 day'
  return `Overdue by ${-days} days`
}

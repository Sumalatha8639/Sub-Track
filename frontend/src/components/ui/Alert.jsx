const TONES = {
  error: 'bg-rose-50 text-rose-800 ring-rose-200',
  success: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  info: 'bg-sky-50 text-sky-800 ring-sky-200',
  warning: 'bg-amber-50 text-amber-900 ring-amber-200',
}

export default function Alert({ tone = 'error', children, className = '' }) {
  if (!children) return null
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={`rounded-lg px-3 py-2 text-sm ring-1 ${TONES[tone]} ${className}`}>
      {children}
    </div>
  )
}

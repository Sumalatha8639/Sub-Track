import { CircleX, Inbox } from 'lucide-react'
import Spinner from './Spinner.jsx'

export function LoadingBlock({ label = 'Loading…' }) {
  return (
    <div className="flex items-center justify-center gap-2 py-16 text-sm text-slate-500" role="status">
      <Spinner className="h-5 w-5 text-indigo-600" />
      {label}
    </div>
  )
}

export function EmptyState({ title, children, action }) {
  return (
    <div className="rounded-xl border border-dashed border-slate-300 bg-white px-6 py-12 text-center">
      <span className="mx-auto inline-flex h-11 w-11 items-center justify-center rounded-full bg-slate-100 text-slate-400">
        <Inbox size={20} aria-hidden="true" />
      </span>
      <p className="mt-3 font-medium text-slate-800">{title}</p>
      {children && <p className="mt-1 text-sm text-slate-500">{children}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function ErrorBlock({ message, onRetry }) {
  return (
    <div className="rounded-xl border border-rose-200 bg-rose-50 px-6 py-10 text-center" role="alert">
      <CircleX size={24} className="mx-auto text-rose-500" aria-hidden="true" />
      <p className="mt-2 text-sm text-rose-800">{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className="mt-3 text-sm font-semibold text-rose-700 hover:underline">
          Try again
        </button>
      )}
    </div>
  )
}

export function Pagination({ page, totalPages, totalElements, onPage }) {
  if (totalPages <= 1) return null
  return (
    <nav className="flex items-center justify-between border-t border-slate-100 px-5 py-3 text-sm" aria-label="Pagination">
      <span className="text-slate-500">
        Page {page + 1} of {totalPages}
        {totalElements !== undefined && ` · ${totalElements} total`}
      </span>
      <div className="flex gap-2">
        <button
          type="button"
          className="rounded-lg px-3 py-1.5 font-medium text-slate-600 ring-1 ring-slate-200 ring-inset hover:bg-slate-50 disabled:text-slate-300"
          disabled={page === 0}
          onClick={() => onPage(page - 1)}
        >
          Previous
        </button>
        <button
          type="button"
          className="rounded-lg px-3 py-1.5 font-medium text-slate-600 ring-1 ring-slate-200 ring-inset hover:bg-slate-50 disabled:text-slate-300"
          disabled={page >= totalPages - 1}
          onClick={() => onPage(page + 1)}
        >
          Next
        </button>
      </div>
    </nav>
  )
}

export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h2>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

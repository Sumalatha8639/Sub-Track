import { Link } from 'react-router'

export default function NotFound() {
  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-2 px-4">
      <p className="text-5xl font-bold text-slate-300">404</p>
      <p className="text-slate-600">Page not found.</p>
      <Link to="/dashboard" className="text-sm font-medium text-indigo-600 hover:underline">
        Go to dashboard
      </Link>
    </div>
  )
}

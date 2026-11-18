import { CalendarCheck } from 'lucide-react'

/** SubTrack mark + word. `tone="light"` for dark backgrounds. */
export default function Logo({ tone = 'dark', size = 'md' }) {
  const box = size === 'lg' ? 'h-10 w-10' : 'h-8 w-8'
  const icon = size === 'lg' ? 22 : 18
  const text = size === 'lg' ? 'text-2xl' : 'text-lg'
  return (
    <span className="inline-flex items-center gap-2.5">
      <span
        className={`${box} inline-flex items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-sm`}
      >
        <CalendarCheck size={icon} strokeWidth={2.25} aria-hidden="true" />
      </span>
      <span className={`${text} font-semibold tracking-tight ${tone === 'light' ? 'text-white' : 'text-slate-900'}`}>
        SubTrack
      </span>
    </span>
  )
}

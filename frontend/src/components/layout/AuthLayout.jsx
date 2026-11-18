import { BellRing, ChartPie, CircleCheck } from 'lucide-react'
import Logo from '../ui/Logo.jsx'

const YEAR = new Date().getFullYear()

const POINTS = [
  { icon: BellRing, title: 'Never miss a renewal', text: 'Email reminders before every due date, at 9 AM your time.' },
  { icon: CircleCheck, title: 'Know what was paid', text: 'Mark every bill paid or missed, with a full audit trail.' },
  { icon: ChartPie, title: 'See where money goes', text: 'Spend by category and month, all in rupees.' },
]

/** Two-panel sign-in layout: brand panel on the left (desktop), form on the right. */
export default function AuthLayout({ title, subtitle, children, footer }) {
  return (
    <div className="flex min-h-full">
      <section className="relative hidden w-[44%] flex-col justify-between overflow-hidden bg-slate-900 p-12 text-white lg:flex">
        <div
          className="pointer-events-none absolute -top-40 -left-40 h-[32rem] w-[32rem] rounded-full bg-indigo-600/30 blur-3xl"
          aria-hidden="true"
        />
        <div
          className="pointer-events-none absolute -right-32 -bottom-48 h-[28rem] w-[28rem] rounded-full bg-violet-600/20 blur-3xl"
          aria-hidden="true"
        />
        <div className="relative">
          <Logo tone="light" size="lg" />
        </div>
        <div className="relative max-w-md">
          <h2 className="text-3xl leading-tight font-semibold tracking-tight">
            Every subscription, renewal and payment in one place.
          </h2>
          <ul className="mt-10 space-y-6">
            {POINTS.map(({ icon: Icon, title: t, text }) => (
              <li key={t} className="flex gap-4">
                <span className="mt-0.5 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white/10 text-indigo-300">
                  <Icon size={18} />
                </span>
                <span>
                  <span className="block font-medium">{t}</span>
                  <span className="mt-0.5 block text-sm text-slate-400">{text}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
        <p className="relative text-xs text-slate-500">© {YEAR} SubTrack</p>
      </section>

      <section className="flex flex-1 flex-col items-center justify-center bg-white px-6 py-10">
        <div className="w-full max-w-sm">
          <div className="mb-8 lg:hidden">
            <Logo />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
          {subtitle && <p className="mt-1.5 text-sm text-slate-500">{subtitle}</p>}
          <div className="mt-8">{children}</div>
          {footer && <div className="mt-8 text-sm text-slate-600">{footer}</div>}
        </div>
      </section>
    </div>
  )
}

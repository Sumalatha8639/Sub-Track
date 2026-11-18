import { Bot, HeartPulse, Package, Smartphone, Tv, Wifi, Zap } from 'lucide-react'

const ICONS = {
  INSURANCE: { Icon: HeartPulse, className: 'bg-rose-50 text-rose-600 ring-rose-100' },
  MOBILE_RECHARGE: { Icon: Smartphone, className: 'bg-sky-50 text-sky-600 ring-sky-100' },
  OTT: { Icon: Tv, className: 'bg-violet-50 text-violet-600 ring-violet-100' },
  AI_TOOL: { Icon: Bot, className: 'bg-emerald-50 text-emerald-600 ring-emerald-100' },
  INTERNET: { Icon: Wifi, className: 'bg-cyan-50 text-cyan-700 ring-cyan-100' },
  UTILITY: { Icon: Zap, className: 'bg-amber-50 text-amber-600 ring-amber-100' },
  OTHER: { Icon: Package, className: 'bg-slate-100 text-slate-600 ring-slate-200' },
}

/** Rounded icon tile for a subscription category; the category name is always shown next to it. */
export default function CategoryIcon({ category, size = 'md' }) {
  const { Icon, className } = ICONS[category] ?? ICONS.OTHER
  const box = size === 'lg' ? 'h-12 w-12' : size === 'sm' ? 'h-8 w-8' : 'h-10 w-10'
  return (
    <span className={`${box} inline-flex shrink-0 items-center justify-center rounded-lg ring-1 ring-inset ${className}`}>
      <Icon size={size === 'lg' ? 22 : size === 'sm' ? 16 : 18} aria-hidden="true" />
    </span>
  )
}

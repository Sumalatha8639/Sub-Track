/** White surface with a hairline border, used for every panel. */
export function Card({ className = '', children, ...props }) {
  return (
    <div className={`rounded-xl border border-slate-200/80 bg-white shadow-[0_1px_2px_rgba(15,23,42,0.04)] ${className}`} {...props}>
      {children}
    </div>
  )
}

export function CardHeader({ title, subtitle, actions, className = '' }) {
  return (
    <div className={`flex flex-wrap items-center gap-3 border-b border-slate-100 px-5 py-4 ${className}`}>
      <div className="mr-auto min-w-0">
        <h2 className="text-[15px] font-semibold text-slate-900">{title}</h2>
        {subtitle && <p className="mt-0.5 text-xs text-slate-500">{subtitle}</p>}
      </div>
      {actions}
    </div>
  )
}

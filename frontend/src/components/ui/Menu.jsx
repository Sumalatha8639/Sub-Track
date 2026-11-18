import { useEffect, useRef, useState } from 'react'

/**
 * Small dropdown menu. `trigger` receives { open, toggle } and renders the button;
 * `items` are { label, icon, onClick, disabled, danger, title } or 'divider'.
 */
export default function Menu({ trigger, items, align = 'right', header }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    const onDown = (e) => ref.current && !ref.current.contains(e.target) && setOpen(false)
    const onKey = (e) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div className="relative" ref={ref}>
      {trigger({ open, toggle: () => setOpen((o) => !o) })}
      {open && (
        <div
          role="menu"
          className={`absolute z-40 mt-1.5 min-w-48 overflow-hidden rounded-lg border border-slate-200 bg-white py-1 shadow-lg ${
            align === 'right' ? 'right-0' : 'left-0'
          }`}
        >
          {header && <div className="border-b border-slate-100 px-3 py-2">{header}</div>}
          {items.map((item, i) =>
            item === 'divider' ? (
              <div key={`d${i}`} className="my-1 border-t border-slate-100" />
            ) : (
              <button
                key={item.label}
                type="button"
                role="menuitem"
                disabled={item.disabled}
                title={item.title}
                onClick={() => {
                  setOpen(false)
                  item.onClick()
                }}
                className={`flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm disabled:cursor-not-allowed disabled:opacity-40 ${
                  item.danger ? 'text-rose-600 hover:bg-rose-50' : 'text-slate-700 hover:bg-slate-50'
                }`}
              >
                {item.icon && <item.icon size={16} className="shrink-0 opacity-70" aria-hidden="true" />}
                {item.label}
              </button>
            ),
          )}
        </div>
      )}
    </div>
  )
}

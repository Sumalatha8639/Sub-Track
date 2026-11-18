import { CircleCheck, CircleX, Info, X } from 'lucide-react'
import { createContext, useCallback, useContext, useMemo, useState } from 'react'

const ToastContext = createContext(null)

const TONES = {
  success: { Icon: CircleCheck, className: 'text-emerald-600' },
  error: { Icon: CircleX, className: 'text-rose-600' },
  info: { Icon: Info, className: 'text-indigo-600' },
}

let nextId = 1

/** Short confirmation messages in the bottom-right corner ("Netflix marked as paid"). */
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  const dismiss = useCallback((id) => setToasts((t) => t.filter((x) => x.id !== id)), [])

  const show = useCallback(
    (title, { tone = 'success', description } = {}) => {
      const id = nextId++
      setToasts((t) => [...t.slice(-2), { id, title, tone, description }])
      setTimeout(() => dismiss(id), 4000)
    },
    [dismiss],
  )

  const value = useMemo(() => ({ show }), [show])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed inset-x-4 bottom-4 z-[60] flex flex-col items-end gap-2 sm:inset-x-auto sm:right-6 sm:bottom-6">
        {toasts.map((t) => {
          const { Icon, className } = TONES[t.tone] ?? TONES.info
          return (
            <div
              key={t.id}
              role="status"
              className="animate-toast-in pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-lg"
            >
              <Icon size={20} className={`mt-0.5 shrink-0 ${className}`} aria-hidden="true" />
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-slate-900">{t.title}</p>
                {t.description && <p className="mt-0.5 text-xs text-slate-500">{t.description}</p>}
              </div>
              <button
                type="button"
                onClick={() => dismiss(t.id)}
                className="rounded p-0.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
                aria-label="Dismiss"
              >
                <X size={16} />
              </button>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast() {
  return useContext(ToastContext) ?? { show: () => {} }
}

import { useEffect, useState } from 'react'
import Spinner from '../ui/Spinner.jsx'

/**
 * Shown while the session is being restored. If the backend is still starting, this can take
 * a while, so after a few seconds we explain the wait.
 */
export default function FullPageLoader() {
  const [slow, setSlow] = useState(false)

  useEffect(() => {
    const t = setTimeout(() => setSlow(true), 3000)
    return () => clearTimeout(t)
  }, [])

  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-3 px-4 text-center" role="status">
      <Spinner className="h-8 w-8 text-indigo-600" />
      {slow ? (
        <>
          <p className="font-medium text-slate-800">Waiting for the server…</p>
          <p className="max-w-xs text-sm text-slate-500">
            The SubTrack backend is still starting up. This can take up to a minute.
          </p>
        </>
      ) : (
        <p className="text-sm text-slate-500">Loading…</p>
      )}
    </div>
  )
}

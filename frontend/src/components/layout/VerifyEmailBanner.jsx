import { useState } from 'react'
import { resendVerification } from '../../api/auth'
import { useAuth } from '../../context/AuthContext.jsx'

/** Reminder emails are only sent to verified addresses, so nudge until verified. */
export default function VerifyEmailBanner() {
  const { user } = useAuth()
  const [state, setState] = useState('idle') // idle | sending | sent | error

  if (!user || user.emailVerified) return null

  const resend = async () => {
    setState('sending')
    try {
      await resendVerification(user.email)
      setState('sent')
    } catch {
      setState('error')
    }
  }

  return (
    <div className="border-b border-amber-200 bg-amber-50">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-3 gap-y-1 px-4 py-2 text-sm text-amber-900">
        <span>
          Verify <b>{user.email}</b> to start getting renewal reminders.
        </span>
        {state === 'sent' ? (
          <span className="font-medium">Sent. Check your inbox.</span>
        ) : (
          <button
            type="button"
            onClick={resend}
            disabled={state === 'sending'}
            className="font-semibold underline underline-offset-2 hover:no-underline disabled:opacity-60"
          >
            {state === 'sending' ? 'Sending…' : 'Resend email'}
          </button>
        )}
        {state === 'error' && <span className="text-rose-700">Couldn't send. Try again later.</span>}
      </div>
    </div>
  )
}

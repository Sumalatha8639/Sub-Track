import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { verifyEmail } from '../api/auth'
import AuthLayout from '../components/layout/AuthLayout.jsx'
import Alert from '../components/ui/Alert.jsx'
import Spinner from '../components/ui/Spinner.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { apiError } from '../utils/errors'

/** Opened from the link in the verification email: /verify-email?token=... */
export default function VerifyEmail() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const { status: authStatus, reloadUser } = useAuth()
  const [state, setState] = useState(token ? 'verifying' : 'error')
  const [message, setMessage] = useState(token ? '' : 'This link is missing its token.')
  const started = useRef(false)

  useEffect(() => {
    // The token is single-use: make sure StrictMode's double effect doesn't spend it twice
    if (!token || started.current) return
    started.current = true
    verifyEmail(token)
      .then((res) => {
        setMessage(res.message)
        setState('done')
      })
      .catch((err) => {
        setMessage(apiError(err).message)
        setState('error')
      })
  }, [token])

  useEffect(() => {
    if (state === 'done' && authStatus === 'authenticated') reloadUser().catch(() => {})
  }, [state, authStatus, reloadUser])

  const next = authStatus === 'authenticated' ? '/dashboard' : '/login'

  return (
    <AuthLayout title="Verify your email">
      {state === 'verifying' && (
        <p className="flex items-center gap-2 text-sm text-slate-600" role="status">
          <Spinner /> Verifying…
        </p>
      )}
      {state === 'done' && <Alert tone="success">{message}</Alert>}
      {state === 'error' && (
        <Alert>
          {message} You can ask for a new link from the banner after logging in.
        </Alert>
      )}
      {state !== 'verifying' && (
        <Link to={next} className="mt-4 inline-block text-sm font-semibold text-indigo-600 hover:underline">
          {authStatus === 'authenticated' ? 'Go to dashboard' : 'Go to login'} →
        </Link>
      )}
    </AuthLayout>
  )
}

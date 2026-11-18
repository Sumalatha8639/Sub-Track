import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { resetPassword } from '../api/auth'
import AuthLayout from '../components/layout/AuthLayout.jsx'
import Alert from '../components/ui/Alert.jsx'
import Button from '../components/ui/Button.jsx'
import Field from '../components/ui/Field.jsx'
import { apiError } from '../utils/errors'

/** Opened from the reset email: /reset-password?token=... */
export default function ResetPassword() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const [form, setForm] = useState({ password: '', confirm: '' })
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState('')
  const [done, setDone] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  const onSubmit = async (e) => {
    e.preventDefault()
    const next = {}
    if (form.password.length < 8) next.password = 'Use at least 8 characters'
    if (form.confirm !== form.password) next.confirm = "Passwords don't match"
    setErrors(next)
    setMessage('')
    if (Object.keys(next).length) return
    setSubmitting(true)
    try {
      const res = await resetPassword(token, form.password)
      setDone(res.message)
    } catch (err) {
      setMessage(apiError(err).message)
    } finally {
      setSubmitting(false)
    }
  }

  if (!token) {
    return (
      <AuthLayout title="Reset your password">
        <Alert>This link is missing its token. Ask for a new one.</Alert>
        <Link to="/forgot-password" className="mt-4 inline-block text-sm font-semibold text-indigo-600 hover:underline">
          Request a new link →
        </Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Choose a new password">
      {done ? (
        <>
          <Alert tone="success">{done}</Alert>
          <Link to="/login" className="mt-4 inline-block text-sm font-semibold text-indigo-600 hover:underline">
            Go to login →
          </Link>
        </>
      ) : (
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          <Alert>{message}</Alert>
          <Field
            label="New password"
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={set('password')}
            error={errors.password}
          />
          <Field
            label="Confirm password"
            type="password"
            autoComplete="new-password"
            value={form.confirm}
            onChange={set('confirm')}
            error={errors.confirm}
          />
          <Button type="submit" className="w-full" loading={submitting}>
            Update password
          </Button>
        </form>
      )}
    </AuthLayout>
  )
}

import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import AuthLayout from '../components/layout/AuthLayout.jsx'
import Alert from '../components/ui/Alert.jsx'
import Button from '../components/ui/Button.jsx'
import Field from '../components/ui/Field.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { apiError } from '../utils/errors'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await login(form.email, form.password)
      navigate(location.state?.from?.pathname ?? '/dashboard', { replace: true })
    } catch (err) {
      setError(apiError(err).message)
      setSubmitting(false)
    }
  }

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  return (
    <AuthLayout
      title="Log in"
      subtitle="Track every renewal in one place."
      footer={
        <>
          New to SubTrack?{' '}
          <Link to="/register" className="font-semibold text-indigo-600 hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <Alert>{error}</Alert>
        <Field label="Email" type="email" autoComplete="email" required value={form.email} onChange={set('email')} />
        <Field
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          value={form.password}
          onChange={set('password')}
        />
        <div className="flex justify-end">
          <Link to="/forgot-password" className="text-sm font-medium text-indigo-600 hover:underline">
            Forgot password?
          </Link>
        </div>
        <Button type="submit" className="w-full" loading={submitting}>
          Log in
        </Button>
      </form>
    </AuthLayout>
  )
}

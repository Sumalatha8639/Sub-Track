import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import AuthLayout from '../components/layout/AuthLayout.jsx'
import Alert from '../components/ui/Alert.jsx'
import Button from '../components/ui/Button.jsx'
import Field from '../components/ui/Field.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { apiError } from '../utils/errors'
import { browserTimezone, timezoneOptions } from '../utils/timezones'

export default function Register() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const zones = useMemo(() => timezoneOptions(), [])
  const [form, setForm] = useState({ name: '', email: '', password: '', timezone: browserTimezone() })
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  const validate = () => {
    const next = {}
    if (!form.name.trim()) next.name = 'Enter your name'
    if (!/^\S+@\S+\.\S+$/.test(form.email)) next.email = 'Enter a valid email'
    if (form.password.length < 8) next.password = 'Use at least 8 characters'
    return next
  }

  const onSubmit = async (e) => {
    e.preventDefault()
    const local = validate()
    setErrors(local)
    setMessage('')
    if (Object.keys(local).length) return
    setSubmitting(true)
    try {
      await register(form)
      navigate('/dashboard', { replace: true })
    } catch (err) {
      const { message: msg, fieldErrors } = apiError(err)
      setErrors(fieldErrors)
      setMessage(msg)
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Get reminders before every renewal."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-indigo-600 hover:underline">
            Log in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <Alert>{message}</Alert>
        <Field label="Name" autoComplete="name" value={form.name} onChange={set('name')} error={errors.name} />
        <Field
          label="Email"
          type="email"
          autoComplete="email"
          value={form.email}
          onChange={set('email')}
          error={errors.email}
        />
        <Field
          label="Password"
          type="password"
          autoComplete="new-password"
          value={form.password}
          onChange={set('password')}
          error={errors.password}
          hint="At least 8 characters"
        />
        <Field
          as="select"
          label="Timezone"
          value={form.timezone}
          onChange={set('timezone')}
          error={errors.timezone}
          hint="Reminders arrive around 9 AM in this timezone"
        >
          {zones.map((z) => (
            <option key={z} value={z}>
              {z}
            </option>
          ))}
        </Field>
        <Button type="submit" className="w-full" loading={submitting}>
          Create account
        </Button>
      </form>
    </AuthLayout>
  )
}

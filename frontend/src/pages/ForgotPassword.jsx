import { useState } from 'react'
import { Link } from 'react-router'
import { forgotPassword } from '../api/auth'
import AuthLayout from '../components/layout/AuthLayout.jsx'
import Alert from '../components/ui/Alert.jsx'
import Button from '../components/ui/Button.jsx'
import Field from '../components/ui/Field.jsx'
import { apiError } from '../utils/errors'

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [done, setDone] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      const res = await forgotPassword(email)
      setDone(res.message)
    } catch (err) {
      setError(apiError(err).message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Reset your password"
      subtitle="We'll email you a link to choose a new one."
      footer={
        <Link to="/login" className="font-semibold text-indigo-600 hover:underline">
          ← Back to login
        </Link>
      }
    >
      {done ? (
        <Alert tone="success">{done}</Alert>
      ) : (
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          <Alert>{error}</Alert>
          <Field label="Email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Button type="submit" className="w-full" loading={submitting} disabled={!email}>
            Send reset link
          </Button>
        </form>
      )}
    </AuthLayout>
  )
}

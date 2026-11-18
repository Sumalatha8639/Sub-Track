import { useMemo, useState } from 'react'
import { changePassword, updateMe } from '../api/auth'
import { ShieldCheck } from 'lucide-react'
import Alert from '../components/ui/Alert.jsx'
import Avatar from '../components/ui/Avatar.jsx'
import Button from '../components/ui/Button.jsx'
import Field from '../components/ui/Field.jsx'
import { PageHeader } from '../components/ui/States.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { apiError } from '../utils/errors'
import { timezoneOptions } from '../utils/timezones'

export default function Profile() {
  return (
    <section className="max-w-3xl space-y-6">
      <PageHeader title="Profile & security" subtitle="Your details, reminder timezone and password." />
      <ProfileSummary />
      <ProfileForm />
      <PasswordForm />
    </section>
  )
}

function ProfileSummary() {
  const { user } = useAuth()
  return (
    <div className="flex flex-wrap items-center gap-4 rounded-xl border border-slate-200/80 bg-white p-5 shadow-[0_1px_2px_rgba(15,23,42,0.04)]">
      <Avatar name={user.name} />
      <div className="mr-auto min-w-0">
        <p className="font-semibold text-slate-900">{user.name}</p>
        <p className="truncate text-sm text-slate-500">{user.email}</p>
      </div>
      {user.emailVerified ? (
        <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">
          <ShieldCheck size={14} aria-hidden="true" /> Email verified
        </span>
      ) : (
        <span className="rounded-full bg-amber-50 px-3 py-1 text-xs font-medium text-amber-800">Email not verified</span>
      )}
    </div>
  )
}

function ProfileForm() {
  const { user, setUser } = useAuth()
  const zones = useMemo(() => timezoneOptions(), [])
  const [form, setForm] = useState({ name: user.name, phone: user.phone ?? '', timezone: user.timezone })
  const [errors, setErrors] = useState({})
  const [status, setStatus] = useState({ tone: 'success', text: '' })
  const [saving, setSaving] = useState(false)

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  const onSubmit = async (e) => {
    e.preventDefault()
    setStatus({ tone: 'success', text: '' })
    if (!form.name.trim()) {
      setErrors({ name: 'Enter your name' })
      return
    }
    setErrors({})
    setSaving(true)
    try {
      setUser(await updateMe({ ...form, name: form.name.trim(), phone: form.phone.trim() }))
      setStatus({ tone: 'success', text: 'Profile saved.' })
    } catch (err) {
      const { message, fieldErrors } = apiError(err)
      setErrors(fieldErrors)
      setStatus({ tone: 'error', text: message })
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4 rounded-xl border border-slate-200/80 bg-white p-6 shadow-[0_1px_2px_rgba(15,23,42,0.04)]" noValidate>
      <h2 className="font-semibold text-slate-900">Your details</h2>
      <Alert tone={status.tone}>{status.text}</Alert>
      <Field label="Email" value={user.email} disabled hint={user.emailVerified ? 'Verified' : 'Not verified yet'} />
      <Field label="Name" value={form.name} onChange={set('name')} error={errors.name} maxLength={100} />
      <Field label="Phone (optional)" type="tel" value={form.phone} onChange={set('phone')} error={errors.phone} />
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
      <div className="flex justify-end">
        <Button type="submit" loading={saving}>
          Save
        </Button>
      </div>
    </form>
  )
}

function PasswordForm() {
  const [form, setForm] = useState({ current: '', next: '', confirm: '' })
  const [errors, setErrors] = useState({})
  const [status, setStatus] = useState({ tone: 'success', text: '' })
  const [saving, setSaving] = useState(false)

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  const onSubmit = async (e) => {
    e.preventDefault()
    setStatus({ tone: 'success', text: '' })
    const next = {}
    if (!form.current) next.current = 'Enter your current password'
    if (form.next.length < 8) next.next = 'Use at least 8 characters'
    if (form.confirm !== form.next) next.confirm = "Passwords don't match"
    setErrors(next)
    if (Object.keys(next).length) return
    setSaving(true)
    try {
      await changePassword(form.current, form.next)
      setForm({ current: '', next: '', confirm: '' })
      setStatus({ tone: 'success', text: 'Password changed. Other devices have been logged out.' })
    } catch (err) {
      setStatus({ tone: 'error', text: apiError(err).message })
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4 rounded-xl border border-slate-200/80 bg-white p-6 shadow-[0_1px_2px_rgba(15,23,42,0.04)]" noValidate>
      <h2 className="font-semibold text-slate-900">Change password</h2>
      <Alert tone={status.tone}>{status.text}</Alert>
      <Field label="Current password" type="password" autoComplete="current-password" value={form.current} onChange={set('current')} error={errors.current} />
      <Field label="New password" type="password" autoComplete="new-password" value={form.next} onChange={set('next')} error={errors.next} />
      <Field label="Confirm new password" type="password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} error={errors.confirm} />
      <div className="flex justify-end">
        <Button type="submit" loading={saving}>
          Change password
        </Button>
      </div>
    </form>
  )
}

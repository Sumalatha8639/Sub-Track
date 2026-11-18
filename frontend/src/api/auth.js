import client, { setAccessToken } from './client'

/** Register, login: the server also sets the refresh cookie. Returns the user. */
async function startSession(url, body) {
  const { data } = await client.post(url, body)
  setAccessToken(data.accessToken)
  return data.user
}

export const register = (body) => startSession('/api/auth/register', body)
export const login = (email, password) => startSession('/api/auth/login', { email, password })

export async function logout() {
  try {
    await client.post('/api/auth/logout')
  } finally {
    setAccessToken(null)
  }
}

export const verifyEmail = (token) => client.post('/api/auth/verify-email', { token }).then((r) => r.data)
export const resendVerification = (email) =>
  client.post('/api/auth/resend-verification', { email }).then((r) => r.data)
export const forgotPassword = (email) => client.post('/api/auth/forgot-password', { email }).then((r) => r.data)
export const resetPassword = (token, newPassword) =>
  client.post('/api/auth/reset-password', { token, newPassword }).then((r) => r.data)
export const changePassword = (currentPassword, newPassword) =>
  client.post('/api/auth/change-password', { currentPassword, newPassword })

export const getMe = () => client.get('/api/users/me').then((r) => r.data)
export const updateMe = (body) => client.put('/api/users/me', body).then((r) => r.data)

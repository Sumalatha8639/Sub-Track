import axios from 'axios'

export const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

/**
 * Shared Axios instance.
 * - The access token lives only in memory (never localStorage), so XSS can't read it from storage.
 * - The refresh token is an httpOnly cookie; withCredentials sends it to /api/auth.
 * - Timeout is generous so slow first requests (backend still starting) don't fail.
 */
const client = axios.create({
  baseURL: API_URL,
  withCredentials: true,
  timeout: 70_000,
  headers: { 'Content-Type': 'application/json' },
})

let accessToken = null
let handlers = { expired: () => {}, refreshed: () => {} }

export function setAccessToken(token) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

/** AuthContext registers callbacks so the UI follows session changes made here. */
export function setSessionHandlers(next) {
  handlers = { ...handlers, ...next }
}

/** Auth endpoints that must never trigger a refresh-and-retry loop. */
const NO_REFRESH = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/refresh',
  '/api/auth/logout',
  '/api/auth/verify-email',
  '/api/auth/resend-verification',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
]

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function postRefresh() {
  const res = await client.post('/api/auth/refresh')
  accessToken = res.data.accessToken
  handlers.refreshed(res.data.user)
  return res.data
}

let inFlight = null

/**
 * Gets a new access token using the refresh cookie. Concurrent callers share one request.
 * With retry=true, a 401 is retried once after a second: another tab may have just rotated
 * the cookie (the server allows a short grace window for this).
 */
export function refreshSession({ retry = false } = {}) {
  if (!inFlight) {
    inFlight = (async () => {
      try {
        return await postRefresh()
      } catch (err) {
        if (retry && err.response?.status === 401) {
          await sleep(1000)
          return await postRefresh()
        }
        throw err
      }
    })().finally(() => {
      inFlight = null
    })
  }
  return inFlight
}

client.interceptors.request.use((config) => {
  if (accessToken && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { config, response } = error
    const url = config?.url ?? ''
    if (response?.status === 401 && config && !config._retried && !NO_REFRESH.some((p) => url.startsWith(p))) {
      config._retried = true
      try {
        await refreshSession({ retry: true })
        config.headers.Authorization = `Bearer ${accessToken}`
        return client(config)
      } catch {
        accessToken = null
        handlers.expired()
      }
    }
    return Promise.reject(error)
  },
)

export default client

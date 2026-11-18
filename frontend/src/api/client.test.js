import { AxiosError } from 'axios'
import client, { getAccessToken, setAccessToken, setSessionHandlers } from './client'

/** Fake transport: route(config) returns { status, data }. Non-2xx rejects like real Axios. */
function useFakeServer(route) {
  const calls = []
  client.defaults.adapter = async (config) => {
    calls.push({ url: config.url, auth: config.headers.Authorization })
    const { status, data } = await route(config, calls)
    const response = { status, data, headers: {}, config, statusText: String(status) }
    if (status >= 200 && status < 300) return response
    throw new AxiosError(`HTTP ${status}`, 'ERR_BAD_RESPONSE', config, null, response)
  }
  return calls
}

const refreshOk = (token) => ({ status: 200, data: { accessToken: token, user: { id: 1, name: 'Asha' } } })

describe('API client token refresh', () => {
  let expired
  let refreshed

  beforeEach(() => {
    expired = vi.fn()
    refreshed = vi.fn()
    setSessionHandlers({ expired, refreshed })
    setAccessToken('old-token')
  })

  it('attaches the access token to requests', async () => {
    const calls = useFakeServer(() => ({ status: 200, data: {} }))

    await client.get('/api/users/me')

    expect(calls[0].auth).toBe('Bearer old-token')
  })

  it('refreshes once on 401 and retries with the new token', async () => {
    const calls = useFakeServer((config) => {
      if (config.url === '/api/auth/refresh') return refreshOk('new-token')
      return config.headers.Authorization === 'Bearer new-token' ? { status: 200, data: { ok: true } } : { status: 401 }
    })

    const res = await client.get('/api/users/me')

    expect(res.data).toEqual({ ok: true })
    expect(calls.map((c) => c.url)).toEqual(['/api/users/me', '/api/auth/refresh', '/api/users/me'])
    expect(getAccessToken()).toBe('new-token')
    expect(refreshed).toHaveBeenCalledWith({ id: 1, name: 'Asha' })
  })

  it('shares one refresh between parallel requests', async () => {
    const calls = useFakeServer((config) => {
      if (config.url === '/api/auth/refresh') return refreshOk('new-token')
      return config.headers.Authorization === 'Bearer new-token' ? { status: 200, data: {} } : { status: 401 }
    })

    await Promise.all([client.get('/api/subscriptions'), client.get('/api/notifications'), client.get('/api/users/me')])

    expect(calls.filter((c) => c.url === '/api/auth/refresh')).toHaveLength(1)
  })

  it('retries the refresh once (another tab may have rotated the cookie)', async () => {
    let refreshes = 0
    useFakeServer((config) => {
      if (config.url === '/api/auth/refresh') {
        refreshes += 1
        return refreshes === 1 ? { status: 401 } : refreshOk('new-token')
      }
      return config.headers.Authorization === 'Bearer new-token' ? { status: 200, data: {} } : { status: 401 }
    })

    await client.get('/api/users/me')

    expect(refreshes).toBe(2)
    expect(expired).not.toHaveBeenCalled()
  })

  it('ends the session when refresh keeps failing', async () => {
    useFakeServer(() => ({ status: 401 }))

    await expect(client.get('/api/users/me')).rejects.toMatchObject({ response: { status: 401 } })
    expect(expired).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBeNull()
  })

  it('never tries to refresh for login failures', async () => {
    const calls = useFakeServer(() => ({ status: 401, data: { message: 'Invalid email or password' } }))

    await expect(client.post('/api/auth/login', {})).rejects.toBeTruthy()
    expect(calls.map((c) => c.url)).toEqual(['/api/auth/login'])
  })
})

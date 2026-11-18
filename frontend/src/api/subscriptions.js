import client from './client'

/** Drops empty filter values so the URL stays clean. */
function cleanParams(params) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== null && v !== undefined))
}

export const listSubscriptions = (params = {}) =>
  client.get('/api/subscriptions', { params: cleanParams(params) }).then((r) => r.data)
export const getSubscription = (id) => client.get(`/api/subscriptions/${id}`).then((r) => r.data)
export const createSubscription = (body) => client.post('/api/subscriptions', body).then((r) => r.data)
export const updateSubscription = (id, body) => client.put(`/api/subscriptions/${id}`, body).then((r) => r.data)
export const deleteSubscription = (id) => client.delete(`/api/subscriptions/${id}`)
export const changeStatus = (id, status) =>
  client.patch(`/api/subscriptions/${id}/status`, { status }).then((r) => r.data)
export const updateReminders = (id, days) =>
  client.put(`/api/subscriptions/${id}/reminders`, { days }).then((r) => r.data)

export const listPeriods = (id) => client.get(`/api/subscriptions/${id}/periods`).then((r) => r.data)
/** body: { status: 'PAID' | 'MISSED', paidOn?, amountPaid?, note? } -> { period, subscription } */
export const markPeriod = (subscriptionId, periodId, body) =>
  client.patch(`/api/subscriptions/${subscriptionId}/periods/${periodId}`, body).then((r) => r.data)
export const periodHistory = (periodId) => client.get(`/api/periods/${periodId}/history`).then((r) => r.data)

export const listNotifications = (page = 0, size = 20) =>
  client.get('/api/notifications', { params: { page, size } }).then((r) => r.data)

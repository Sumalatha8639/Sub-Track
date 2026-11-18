import client from './client'

const clean = (params) =>
  Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v !== null && v !== undefined))

export const getSummary = () => client.get('/api/dashboard/summary').then((r) => r.data)
export const getStatusBoard = (params = {}) =>
  client.get('/api/dashboard/status-board', { params: clean(params) }).then((r) => r.data)
export const getCategoryBreakdown = () => client.get('/api/dashboard/category-breakdown').then((r) => r.data)
/** Bills paid in a month ("2026-11"); omit the month for the current one. */
export const getPaid = (month) =>
  client.get('/api/dashboard/paid', { params: clean({ month }) }).then((r) => r.data)
export const getTrend =(months = 12) => client.get('/api/dashboard/trend', { params: { months } }).then((r) => r.data)

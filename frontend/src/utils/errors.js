/**
 * Turns an Axios error into { message, fieldErrors } using the backend's standard error body
 * ({ status, message, fieldErrors }).
 */
export function apiError(err, fallback = 'Something went wrong. Please try again.') {
  if (!err?.response) {
    if (err?.code === 'ECONNABORTED') {
      return { message: 'The server took too long to respond. Please try again.', fieldErrors: {} }
    }
    return { message: "Can't reach the server. Check your connection and try again.", fieldErrors: {} }
  }
  const data = err.response.data ?? {}
  return {
    message: data.message || fallback,
    fieldErrors: data.fieldErrors ?? {},
    status: err.response.status,
  }
}

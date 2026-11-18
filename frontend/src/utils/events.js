/** Fired after anything that changes subscriptions or payments, so shared UI (the bell) can refresh. */
export const DATA_CHANGED = 'subtrack:data-changed'

export function notifyDataChanged() {
  window.dispatchEvent(new Event(DATA_CHANGED))
}

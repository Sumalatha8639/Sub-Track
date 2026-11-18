export const DEFAULT_TIMEZONE = 'Asia/Kolkata'

/** All IANA zones the browser knows, with the default first. */
export function timezoneOptions() {
  let zones = []
  try {
    zones = Intl.supportedValuesOf('timeZone')
  } catch {
    zones = [DEFAULT_TIMEZONE, 'UTC']
  }
  return [DEFAULT_TIMEZONE, ...zones.filter((z) => z !== DEFAULT_TIMEZONE)]
}

/** The browser's zone, falling back to India. */
export function browserTimezone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || DEFAULT_TIMEZONE
  } catch {
    return DEFAULT_TIMEZONE
  }
}

/** Initials in a circle, e.g. "Demo User" -> "DU". */
export default function Avatar({ name = '', size = 'md' }) {
  const initials =
    name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0].toUpperCase())
      .join('') || '?'
  const box = size === 'sm' ? 'h-8 w-8 text-xs' : 'h-9 w-9 text-sm'
  return (
    <span
      className={`${box} inline-flex shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 font-semibold text-white`}
      aria-hidden="true"
    >
      {initials}
    </span>
  )
}

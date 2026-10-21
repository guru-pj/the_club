import { StarIcon } from './icons'

/** A rating shown as five stars (rounded to the nearest half star in the label, whole stars drawn). */
export function Stars({ value, size = 16 }: { value: number | null | undefined; size?: number }) {
  const rounded = value == null ? 0 : Math.round(value)
  return (
    <span className="stars" role="img" aria-label={value == null ? 'No ratings yet' : `${value.toFixed(1)} out of 5 stars`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} className={n <= rounded ? 'star on' : 'star'} aria-hidden="true"><StarIcon size={size} /></span>
      ))}
    </span>
  )
}

/** Pick 1–5 stars; a radio group underneath, so it works with a keyboard and screen readers. */
export function StarInput({ value, onChange, name }: { value: number; onChange: (n: number) => void; name: string }) {
  return (
    <fieldset className="star-input">
      <legend className="visually-hidden">Your rating</legend>
      {[1, 2, 3, 4, 5].map((n) => (
        <label key={n} className={n <= value ? 'star on' : 'star'} title={`${n} star${n === 1 ? '' : 's'}`}>
          <input type="radio" name={name} value={n} checked={value === n} onChange={() => onChange(n)} className="visually-hidden" />
          <StarIcon size={28} />
          <span className="visually-hidden">{n} star{n === 1 ? '' : 's'}</span>
        </label>
      ))}
    </fieldset>
  )
}

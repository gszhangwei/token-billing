export const IDEMPOTENCY_KEY_RE = /^[A-Za-z0-9_-]{8,255}$/

export function validateIdempotencyKey(value: string): string {
  if (!value) return ''
  if (!IDEMPOTENCY_KEY_RE.test(value)) {
    return 'Must be 8–255 characters: letters, digits, _ or -'
  }
  return ''
}

export function validateTokens(value: string): string {
  if (value === '') return 'Required'
  const n = Number(value)
  if (!Number.isInteger(n) || n < 0) return 'Must be a non-negative integer'
  return ''
}

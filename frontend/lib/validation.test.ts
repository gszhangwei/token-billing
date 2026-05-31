import { describe, it, expect } from 'vitest'
import { validateIdempotencyKey, validateTokens } from './validation'

describe('validateTokens', () => {
  it('returns "Required" for empty string', () => {
    expect(validateTokens('')).toBe('Required')
  })

  it('accepts 0', () => {
    expect(validateTokens('0')).toBe('')
  })

  it('accepts large positive integer', () => {
    expect(validateTokens('1000000')).toBe('')
  })

  it('rejects negative numbers', () => {
    expect(validateTokens('-1')).not.toBe('')
  })

  it('rejects floats', () => {
    expect(validateTokens('1.5')).not.toBe('')
  })

  it('rejects non-numeric strings', () => {
    expect(validateTokens('abc')).not.toBe('')
  })
})

describe('validateIdempotencyKey', () => {
  it('returns empty for blank input (field is optional)', () => {
    expect(validateIdempotencyKey('')).toBe('')
  })

  it('accepts 8-character alphanumeric key', () => {
    expect(validateIdempotencyKey('AbCd1234')).toBe('')
  })

  it('accepts key with underscores and hyphens', () => {
    expect(validateIdempotencyKey('req-abc_12345678')).toBe('')
  })

  it('accepts 255-character key', () => {
    expect(validateIdempotencyKey('a'.repeat(255))).toBe('')
  })

  it('rejects 7-character key (too short)', () => {
    expect(validateIdempotencyKey('abc1234')).not.toBe('')
  })

  it('rejects 256-character key (too long)', () => {
    expect(validateIdempotencyKey('a'.repeat(256))).not.toBe('')
  })

  it('rejects key with spaces', () => {
    expect(validateIdempotencyKey('req key 123')).not.toBe('')
  })

  it('rejects key with special chars like @', () => {
    expect(validateIdempotencyKey('req@key12345')).not.toBe('')
  })
})

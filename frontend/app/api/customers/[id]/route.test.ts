import { describe, it, expect } from 'vitest'
import { GET } from './route'

function ctx(id: string) {
  return { params: { id } }
}

describe('GET /api/customers/[id]', () => {
  it('returns 200 with full info for CUST-001 (active Starter)', async () => {
    const res = GET(new Request('http://localhost'), ctx('CUST-001'))
    expect(res.status).toBe(200)
    const body = await res.json()
    expect(body.id).toBe('CUST-001')
    expect(body.planName).toBe('Starter')
    expect(body.monthlyQuota).toBe(100000)
    expect(body.usedTokens).toBe(80000)
    expect(body.status).toBe('active')
  })

  it('returns 200 with free-tier info for CUST-002', async () => {
    const res = GET(new Request('http://localhost'), ctx('CUST-002'))
    const body = await res.json()
    expect(body.planName).toBe('Free Tier')
    expect(body.monthlyQuota).toBe(10000)
  })

  it('returns expired status for CUST-004', async () => {
    const res = GET(new Request('http://localhost'), ctx('CUST-004'))
    const body = await res.json()
    expect(body.status).toBe('expired')
    expect(body.monthlyQuota).toBeNull()
  })

  it('returns conflict status for CUST-005', async () => {
    const res = GET(new Request('http://localhost'), ctx('CUST-005'))
    const body = await res.json()
    expect(body.status).toBe('conflict')
  })

  it('returns no-subscription status for CUST-006', async () => {
    const res = GET(new Request('http://localhost'), ctx('CUST-006'))
    const body = await res.json()
    expect(body.status).toBe('no-subscription')
  })

  it('returns 404 for unknown customer ID', async () => {
    const res = GET(new Request('http://localhost'), ctx('CUST-999'))
    expect(res.status).toBe(404)
    const body = await res.json()
    expect(body.title).toBe('Customer not found')
    expect(body.detail).toContain('CUST-999')
  })
})

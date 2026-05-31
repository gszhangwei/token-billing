import { describe, it, expect, vi, beforeEach } from 'vitest'
import { POST } from './route'

const mockFetch = vi.fn()
vi.stubGlobal('fetch', mockFetch)

function makeRequest(opts: {
  body?: string
  headers?: Record<string, string>
} = {}): Request {
  return new Request('http://localhost/api/billing-proxy', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...opts.headers },
    body: opts.body ?? '{}',
  })
}

function backendOk(status: number, body: unknown, extraHeaders?: Record<string, string>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...extraHeaders },
  })
}

describe('POST /api/billing-proxy', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    process.env.BILLING_BACKEND_URL = 'http://test-backend:8080'
  })

  it('forwards body to backend /api/usage and returns 201', async () => {
    mockFetch.mockResolvedValueOnce(backendOk(201, { billId: 'B-001' }))

    const payload = JSON.stringify({ customerId: 'CUST-001', promptTokens: 100, completionTokens: 50 })
    const res = await POST(makeRequest({ body: payload }))

    expect(res.status).toBe(201)
    const [url, opts] = mockFetch.mock.calls[0] as [string, RequestInit & { headers: Headers }]
    expect(url).toBe('http://test-backend:8080/api/usage')
    expect(opts.method).toBe('POST')
    expect(opts.body).toBe(payload)
  })

  it('forwards Authorization and Idempotency-Key headers', async () => {
    mockFetch.mockResolvedValueOnce(backendOk(201, {}))

    await POST(
      makeRequest({
        headers: {
          Authorization: 'Bearer my-token',
          'Idempotency-Key': 'req-abc-123',
        },
      }),
    )

    const [, opts] = mockFetch.mock.calls[0] as [string, { headers: Headers }]
    expect(opts.headers.get('Authorization')).toBe('Bearer my-token')
    expect(opts.headers.get('Idempotency-Key')).toBe('req-abc-123')
  })

  it('omits headers when not present in request', async () => {
    mockFetch.mockResolvedValueOnce(backendOk(201, {}))

    await POST(makeRequest())

    const [, opts] = mockFetch.mock.calls[0] as [string, { headers: Headers }]
    expect(opts.headers.get('Authorization')).toBeNull()
    expect(opts.headers.get('Idempotency-Key')).toBeNull()
  })

  it('forwards Idempotent-Replayed header from backend on 200 replay', async () => {
    mockFetch.mockResolvedValueOnce(
      backendOk(200, { billId: 'B-001' }, { 'Idempotent-Replayed': 'true' }),
    )

    const res = await POST(makeRequest())

    expect(res.status).toBe(200)
    expect(res.headers.get('Idempotent-Replayed')).toBe('true')
  })

  it('passes through 4xx error responses from backend', async () => {
    mockFetch.mockResolvedValueOnce(
      new Response(
        JSON.stringify({ type: 'about:blank', title: 'Customer not found', status: 404 }),
        { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    )

    const res = await POST(makeRequest({ body: JSON.stringify({ customerId: 'CUST-MISSING' }) }))

    expect(res.status).toBe(404)
    const json = await res.json()
    expect(json.status).toBe(404)
  })

  it('returns 502 when backend is unreachable', async () => {
    mockFetch.mockRejectedValueOnce(new TypeError('fetch failed'))

    const res = await POST(makeRequest())

    expect(res.status).toBe(502)
    const json = await res.json()
    expect(json.title).toBe('Gateway Error')
    expect(json.status).toBe(502)
  })

  it('falls back to localhost:8080 when env var is unset', async () => {
    delete process.env.BILLING_BACKEND_URL
    mockFetch.mockResolvedValueOnce(backendOk(201, {}))

    await POST(makeRequest())

    const [url] = mockFetch.mock.calls[0] as [string]
    expect(url).toBe('http://localhost:8080/api/usage')
  })
})

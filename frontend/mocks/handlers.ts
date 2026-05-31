import { http, HttpResponse } from 'msw'

export const MOCK_BILL = {
  billId: 'B-TEST-001',
  customerId: 'CUST-001',
  promptTokens: 100,
  completionTokens: 50,
  totalTokens: 150,
  tokensFromQuota: 150,
  overageTokens: 0,
  totalCharge: '0.00',
  currency: 'USD',
  calculatedAt: '2026-01-01T00:00:00Z',
}

export const MOCK_CUSTOMER = {
  id: 'CUST-001',
  name: 'Acme Corp',
  planName: 'Starter',
  monthlyQuota: 100000,
  usedTokens: 80000,
  overageRatePer1k: 0.002,
  status: 'active',
}

export const handlers = [
  http.get('/api/customers/:id', ({ params }) => {
    if (params.id === 'CUST-001') {
      return HttpResponse.json(MOCK_CUSTOMER)
    }
    return HttpResponse.json(
      {
        type: 'about:blank',
        title: 'Customer not found',
        status: 404,
        detail: `No customer with id ${params.id}`,
      },
      { status: 404 },
    )
  }),

  http.post('/api/billing-proxy', () => {
    return HttpResponse.json(MOCK_BILL, { status: 201 })
  }),
]

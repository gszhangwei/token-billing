export const runtime = 'edge'

import { getCustomerInfo } from '../../../../lib/customers'

export function GET(
  _request: Request,
  { params }: { params: { id: string } },
): Response {
  const info = getCustomerInfo(params.id)

  if (!info) {
    return new Response(
      JSON.stringify({
        type: 'about:blank',
        title: 'Customer not found',
        status: 404,
        detail: `Customer '${params.id}' does not exist`,
      }),
      { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
    )
  }

  return new Response(JSON.stringify(info), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

export const runtime = 'edge'

const BACKEND_USAGE_PATH = '/api/usage'

export async function POST(request: Request): Promise<Response> {
  const backendBase = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'
  const targetUrl = `${backendBase}${BACKEND_USAGE_PATH}`

  const body = await request.text()

  const forwardHeaders = new Headers({
    'Content-Type': 'application/json',
  })

  const auth = request.headers.get('Authorization')
  if (auth) forwardHeaders.set('Authorization', auth)

  const idempotencyKey = request.headers.get('Idempotency-Key')
  if (idempotencyKey) forwardHeaders.set('Idempotency-Key', idempotencyKey)

  let backendResponse: Response
  try {
    backendResponse = await fetch(targetUrl, {
      method: 'POST',
      headers: forwardHeaders,
      body,
    })
  } catch {
    return new Response(
      JSON.stringify({
        type: 'https://tools.ietf.org/html/rfc7807',
        title: 'Gateway Error',
        status: 502,
        detail: 'Failed to reach billing backend',
      }),
      {
        status: 502,
        headers: { 'Content-Type': 'application/problem+json' },
      },
    )
  }

  const responseBody = await backendResponse.text()

  const responseHeaders = new Headers()
  const contentType = backendResponse.headers.get('Content-Type')
  if (contentType) responseHeaders.set('Content-Type', contentType)

  const idempotentReplayed = backendResponse.headers.get('Idempotent-Replayed')
  if (idempotentReplayed) responseHeaders.set('Idempotent-Replayed', idempotentReplayed)

  return new Response(responseBody, {
    status: backendResponse.status,
    headers: responseHeaders,
  })
}
